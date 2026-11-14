package com.jpage4500.reddittv.manager

import com.jpage4500.reddittv.data.*
import com.jpage4500.reddittv.util.DateUtils
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.WebSession
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val log = LoggerConfig.createLogger("RedditClient")

/**
 * reads reddit the way its own web page does: the `.json` view of any page, fetched with the
 * cookies of the app's browser ([WebSession]) so the feed is the signed-in user's — no API key,
 * no OAuth app. votes and saves go to the same /api endpoints the old web UI posts to, with the
 * session's modhash.
 *
 * see docs/How It Works.md
 */
object RedditClient {
    private const val BASE = "https://www.reddit.com"

    // comments read with a post
    private const val COMMENT_LIMIT = 200

    // a browser's UA when the WebView can't tell us its own (iOS): reddit turns away clients that
    // don't look like one
    private const val FALLBACK_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

    // a page of a feed
    private const val PAGE_SIZE = 25

    // reddit's RSS allows about one request every second or two: they're spaced at least this far
    // apart, and a 429 waits out its x-ratelimit-reset (at most RSS_MAX_WAIT_SEC) and asks once more
    private const val RSS_GAP_MS = 2_000L
    private const val RSS_MAX_WAIT_SEC = 8L

    // signed out, when reddit won't give its communities: a fixed few for the menu
    private val DEFAULT_SUBREDDITS = listOf(
        "aww", "earthporn", "food", "funny", "dataisbeautiful", "gaming", "mildlyinteresting", "movies",
        "music", "nba", "nfl", "oddlysatisfying", "pics", "science", "space", "sports", "television",
        "todayilearned", "videos", "worldnews",
    )

    private val rssLock = Mutex()
    private var lastRssMs = 0L

    // cookies come from WebSession on every call, not a Ktor cookie jar
    private val httpClient = createHttpClient(useCookies = false)

    private val _user = MutableStateFlow<RedditUser?>(null)

    /** the signed-in user, or null when browsing signed out. see [refreshUser] */
    val user: StateFlow<RedditUser?> = _user.asStateFlow()

    val isSignedIn: Boolean
        get() = WebSession.isSignedIn(SignInSite.REDDIT)

    /** a page of [feed], newest page after [after]; [topRange] is top's time range ("day", "week", …) */
    suspend fun getFeed(feed: Feed, sort: FeedSort, after: String? = null, topRange: String = "day"): Result<Listing> = runCatching {
        // hot is a listing's default order: the bare listing answers where /hot.json is turned away
        val path = if (sort == FeedSort.HOT) feed.path else "${feed.path}/${sort.path}"
        val query = buildString {
            append("?raw_json=1&limit=$PAGE_SIZE")
            if (sort == FeedSort.TOP) append("&t=").append(topRange)
            if (after != null) append("&after=").append(after)
        }
        // home + hot is the bare root: "/.json"
        val jsonPath = path.ifEmpty { "/" } + ".json"
        try {
            RedditParser.parseListing(get("$BASE$jsonPath$query"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (isSignedIn && RedditScraper.isSupported) {
                log.w("getFeed: ${feed.path}: ${e.message}, scraping")
                RedditScraper.listing("$path/$query")?.takeIf { it.isNotEmpty() }?.let { scraped ->
                    val listing = RedditParser.parseListing(scraped)
                    log.d("getFeed: scraped ${mediaSummary(listing.posts)}")
                    return@runCatching listing.copy(posts = withMedia(listing.posts))
                }
            }
            // signed out, old reddit (the scrape) only shows its login page; signed in, reddit turns
            // some sorts away (/new.json: 403) where the desktop can't scrape. RSS still answers both
            log.w("getFeed: ${feed.path}: ${e.message}, reading RSS")
            val listing = RedditRss.parseListing(getRss("$BASE${path.trimEnd('/')}/.rss$query"), PAGE_SIZE)
            // signed in, /api/info.json fills in the scores, comment counts and media RSS lacks
            if (isSignedIn) listing.copy(posts = withMedia(listing.posts)) else listing
        }
    }.onFailure { log.e("getFeed: ${feed.path}: ${it.message}") }

    /** a post with its comments */
    suspend fun getPost(permalink: String): Result<PostDetail> = runCatching {
        val path = permalink.trimEnd('/')
        try {
            RedditParser.parsePostDetail(get("$BASE$path.json?raw_json=1&limit=$COMMENT_LIMIT"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!isSignedIn) {
                log.w("getPost: $permalink: ${e.message}, reading RSS")
                return@runCatching RedditRss.parsePostDetail(getRss("$BASE$path/.rss?limit=$COMMENT_LIMIT")) ?: error("no post in feed")
            }
            if (!RedditScraper.isSupported) throw e
            log.w("getPost: $permalink: ${e.message}, scraping")
            val scraped = RedditScraper.post(path, COMMENT_LIMIT) ?: throw e
            RedditParser.parsePostDetail(scraped)?.let { it.copy(post = withMedia(listOf(it.post)).first()) }
        } ?: error("no post in response")
    }.onFailure { log.e("getPost: $permalink: ${it.message}") }

    /** what kinds of media [posts] have, for the log: {Video=3, Image=12, …} */
    private fun mediaSummary(posts: List<Post>): Map<String?, Int> =
        posts.groupingBy { it.media::class.simpleName }.eachCount()

    /**
     * scraped posts may lack their media (RedditScraper reads what old reddit's page shows): ask
     * /api/info.json for the full posts, keeping any it doesn't answer for as they are
     */
    private suspend fun withMedia(posts: List<Post>): List<Post> {
        if (posts.isEmpty()) return posts
        val ids = posts.joinToString(",") { it.fullName }
        val full = try {
            RedditParser.parseListing(get("$BASE/api/info.json?raw_json=1&id=$ids")).posts.associateBy { it.fullName }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.w("withMedia: ${e.message}")
            return posts
        }
        log.d("withMedia: ${full.size} of ${posts.size}")
        return posts.map { full[it.fullName] ?: it }
    }

    /** the user's subscriptions, or popular subreddits when signed out */
    suspend fun getSubreddits(): Result<List<Subreddit>> = runCatching {
        val path = if (isSignedIn) "/subreddits/mine/subscriber.json" else "/subreddits/popular.json"
        val all = mutableListOf<Subreddit>()
        var after: String? = null
        // a few pages at most: the sidebar isn't meant for thousands
        try {
            for (page in 1..4) {
                val url = "$BASE$path?raw_json=1&limit=100" + (after?.let { "&after=$it" } ?: "")
                val (subs, next) = RedditParser.parseSubreddits(get(url))
                all += subs
                after = next ?: break
            }
        } catch (e: Exception) {
            if (e is CancellationException || isSignedIn) throw e
            log.w("getSubreddits: ${e.message}, using the defaults")
            return@runCatching DEFAULT_SUBREDDITS.map { Subreddit(it, "", null, 0) }
        }
        // the sidebar keys its rows by name: a repeat would crash it
        all.distinctBy { it.name.lowercase() }.sortedBy { it.name.lowercase() }
    }.onFailure { log.e("getSubreddits: ${it.message}") }

    /**
     * communities matching [query] as it's typed: reddit's own search-box suggestions, or its
     * community search when those are turned away
     */
    suspend fun searchSubreddits(query: String, includeNsfw: Boolean): Result<List<Subreddit>> = runCatching {
        val q = query.trim().encodeURLQueryComponent()
        val subs = try {
            RedditParser.parseSubreddits(
                get("$BASE/api/subreddit_autocomplete_v2.json?raw_json=1&limit=10&include_profiles=false&include_over_18=$includeNsfw&query=$q")
            ).first
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.w("searchSubreddits: autocomplete: ${e.message}, searching")
            RedditParser.parseSubreddits(get("$BASE/subreddits/search.json?raw_json=1&limit=10&include_over_18=$includeNsfw&q=$q")).first
        }
        subs.distinctBy { it.name.lowercase() }
    }.onFailure { if (it !is CancellationException) log.e("searchSubreddits: ${it.message}") }

    /** read who's signed in (and the modhash for votes). null user when signed out */
    suspend fun refreshUser(): RedditUser? {
        if (!isSignedIn) {
            _user.value = null
            return null
        }
        val user = runCatching { RedditParser.parseMe(get("$BASE/api/me.json")) }
            .onFailure { log.e("refreshUser: ${it.message}") }
            .getOrNull()
        log.d("refreshUser: ${user?.name}")
        _user.value = user
        return user
    }

    /** dir: 1 up, -1 down, 0 clear. [fullName] is a post's or comment's "t3_…"/"t1_…" */
    suspend fun vote(fullName: String, dir: Int): Result<Unit> = post("/api/vote", "id" to fullName, "dir" to dir.toString())

    suspend fun save(fullName: String, save: Boolean): Result<Unit> = post(if (save) "/api/save" else "/api/unsave", "id" to fullName)

    /** join ([subscribe] true) or leave the community [name] ("space", not "r/space") */
    suspend fun subscribe(name: String, subscribe: Boolean): Result<Unit> {
        val action = "action" to if (subscribe) "sub" else "unsub"
        // joining shouldn't also subscribe a new account to reddit's default communities. sub only:
        // reddit answers an unsub that carries it with a 400
        val extra = if (subscribe) arrayOf("skip_initial_defaults" to "true") else emptyArray()
        val byName = post("/api/subscribe", action, "sr_name" to name, *extra)
        if (byName.isSuccess) return byName
        // turned away by name: try the community's id ("t5_…"), as reddit's own page sends it
        val fullName = runCatching { RedditParser.parseSubredditFullName(get("$BASE/r/$name/about.json")) }
            .onFailure { log.e("subscribe: about $name: ${it.message}") }
            .getOrNull() ?: return byName
        log.d("subscribe: $name by name failed, retrying as $fullName")
        return post("/api/subscribe", action, "sr" to fullName, *extra)
    }

    fun signOut() {
        WebSession.signOut(SignInSite.REDDIT)
        _user.value = null
    }

    private suspend fun get(url: String): String {
        val response = httpClient.get(url) { sessionHeaders(url) }
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
        return response.bodyAsText()
    }

    /**
     * a feed's RSS — signed out, where reddit turns `.json` away. one at a time, [RSS_GAP_MS] apart,
     * and a 429 is waited out once
     */
    private suspend fun getRss(url: String): String = rssLock.withLock {
        val wait = lastRssMs + RSS_GAP_MS - DateUtils.nowMs()
        if (wait > 0) delay(wait)
        suspend fun ask() = httpClient.get(url) {
            header(HttpHeaders.UserAgent, WebSession.userAgent ?: FALLBACK_USER_AGENT)
            header(HttpHeaders.Accept, "application/atom+xml")
        }
        var response = ask()
        if (response.status == HttpStatusCode.TooManyRequests) {
            val resetSec = response.headers["x-ratelimit-reset"]?.toDoubleOrNull()?.toLong() ?: 2L
            log.w("getRss: 429, waiting ${resetSec}s")
            delay(resetSec.coerceIn(1L, RSS_MAX_WAIT_SEC) * 1000)
            response = ask()
        }
        lastRssMs = DateUtils.nowMs()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
        response.bodyAsText()
    }

    private suspend fun post(path: String, vararg fields: Pair<String, String>): Result<Unit> = runCatching {
        val modHash = _user.value?.modHash ?: refreshUser()?.modHash ?: error("not signed in")
        val url = "$BASE$path"
        val response = httpClient.submitForm(
            url = url,
            formParameters = parameters {
                fields.forEach { (k, v) -> append(k, v) }
                append("uh", modHash)
            },
        ) {
            sessionHeaders(url)
            header("X-Modhash", modHash)
        }
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
    }.onFailure { log.e("post: $path: ${it.message}") }

    private fun HttpRequestBuilder.sessionHeaders(url: String) {
        header(HttpHeaders.UserAgent, WebSession.userAgent ?: FALLBACK_USER_AGENT)
        header(HttpHeaders.Accept, "application/json")
        WebSession.cookies(url)?.let { header(HttpHeaders.Cookie, it) }
    }
}
