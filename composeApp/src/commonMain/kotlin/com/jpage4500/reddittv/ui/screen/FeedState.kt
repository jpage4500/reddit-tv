package com.jpage4500.reddittv.ui.screen

import androidx.compose.runtime.*
import com.jpage4500.reddittv.data.*
import com.jpage4500.reddittv.manager.RedditClient
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.*
import reddittv.composeapp.generated.resources.*

private val log = LoggerConfig.createLogger("FeedState")

/**
 * the posts of one [Feed], paged as the user scrolls. lives as long as the screen (see
 * [rememberFeedState]) so going back from a post keeps the position and the pages loaded
 */
class FeedState(
    val feed: Feed,
    val sort: FeedSort,
    private val scope: CoroutineScope,
) {
    // what the feed shows: [loaded] minus what the hide settings filter out
    val posts = mutableStateListOf<Post>()
    // every post loaded so far, so a changed hide setting re-filters without a reload
    private val loaded = mutableListOf<Post>()
    private var hidePinned = SettingsManager.getBoolean(AppPref.HIDE_PINNED)
    private var hideNoMedia = SettingsManager.getBoolean(AppPref.HIDE_NO_MEDIA)
    // a mostly-text feed (r/programming): "hide posts without media" would leave it nearly empty,
    // so this feed shows them anyway
    private var isMostlyText = false
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var isEnd by mutableStateOf(false)
        private set
    // the last few pages were all hidden by the settings: stop paging in on our own until asked
    // (a feed like r/programming with "hide posts without media" would otherwise page forever)
    var isStalled by mutableStateOf(false)
        private set

    private var after: String? = null
    // top: which of [TOP_RANGES] is being paged. when today's top runs out the feed carries on into
    // the week's (then month, year, all time), skipping posts it already has
    private var topRange = 0
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        posts.clear()
        loaded.clear()
        after = null
        topRange = 0
        isEnd = false
        isStalled = false
        isMostlyText = false
        loadMore()
    }

    /** the user asked for more (the "Load more" button): resumes paging after [isStalled] */
    fun loadMoreAnyway() {
        isStalled = false
        loadMore()
    }

    /**
     * the next page; ignored while one is loading, at the end, or [isStalled]. [pagesLeft] caps how
     * many pages one call fetches looking for posts the hide settings let through
     */
    fun loadMore(pagesLeft: Int = MAX_PAGES) {
        if (isLoading || isEnd || isStalled) return
        isLoading = true
        error = null
        job = scope.launch {
            var shownCount = 0
            val range = TOP_RANGES[topRange]
            RedditClient.getFeed(feed, sort, after, range)
                .onSuccess { page ->
                    // a page can repeat a post that moved up while scrolling, and a longer top range
                    // repeats the shorter one's posts
                    val ids = loaded.mapTo(HashSet()) { it.id }
                    val added = page.posts.filter { it.id !in ids }
                    loaded += added
                    after = page.after
                    if (page.after == null && sort == FeedSort.TOP && topRange < TOP_RANGES.lastIndex) {
                        // the end of this range's top: the next page comes from a longer one
                        topRange++
                        log.d("loadMore: ${feed.path}: end of top/$range, next: top/${TOP_RANGES[topRange]}")
                    } else {
                        isEnd = page.after == null
                    }
                    if (checkMostlyText()) {
                        shownCount = posts.size
                    } else {
                        val shown = added.filter { isShown(it) }
                        posts += shown
                        shownCount = shown.size
                    }
                    log.d("loadMore: ${feed.path}: +${page.posts.size}, shown:$shownCount, total:${posts.size}")
                }
                .onFailure { error = it.message ?: getString(Res.string.feed_load_failed, feed.label.ifEmpty { getString(Res.string.feed_home) }) }
            isLoading = false
            // a page the hide settings emptied adds nothing to scroll towards: fetch the next one,
            // up to [MAX_PAGES] in a row
            if (error == null && !isEnd && (shownCount == 0 || posts.size < MIN_SHOWN)) {
                if (pagesLeft > 1) loadMore(pagesLeft - 1)
                else if (hideNoMedia && !isMostlyText && posts.size < MIN_SHOWN) {
                    // still too few to fill the screen: give up hiding the text posts
                    log.d("loadMore: ${feed.path}: only ${posts.size} with media after $MAX_PAGES pages, showing all")
                    isMostlyText = true
                    refilter()
                } else {
                    log.d("loadMore: ${feed.path}: stalled after $MAX_PAGES pages, shown:${posts.size}")
                    isStalled = true
                }
            }
        }
    }

    /** apply changed hide settings to the posts already loaded */
    fun setFilters(hidePinned: Boolean, hideNoMedia: Boolean) {
        if (hidePinned == this.hidePinned && hideNoMedia == this.hideNoMedia) return
        this.hidePinned = hidePinned
        this.hideNoMedia = hideNoMedia
        isMostlyText = false
        if (!checkMostlyText()) refilter()
        log.d("setFilters: hidePinned:$hidePinned, hideNoMedia:$hideNoMedia, shown:${posts.size}/${loaded.size}")
        isStalled = false
        if (!isEnd && posts.size < MIN_SHOWN) loadMore()
    }

    private fun isShown(post: Post): Boolean {
        if (hidePinned && post.isStickied) return false
        if (hideNoMedia && !isMostlyText && !post.hasMedia) return false
        return true
    }

    /** rebuild [posts] from [loaded] with the current filters */
    private fun refilter() {
        val shown = loaded.filter { isShown(it) }
        posts.clear()
        posts += shown
    }

    /**
     * with "hide posts without media" on, decide from what's loaded whether this is a mostly-text
     * feed — fewer than [MIN_MEDIA_SHARE] of a page's worth with media — and if so show everything.
     * true when it just switched (and [posts] was rebuilt)
     */
    private fun checkMostlyText(): Boolean {
        // a feed that ended short (r/programming's top of the day: 7 posts) is judged on what it has
        if (!hideNoMedia || isMostlyText || loaded.isEmpty() || (loaded.size < MIN_SAMPLE && !isEnd)) return false
        val withMedia = loaded.count { it.hasMedia }
        if (withMedia >= loaded.size * MIN_MEDIA_SHARE) return false
        log.d("checkMostlyText: ${feed.path}: $withMedia/${loaded.size} with media, showing all")
        isMostlyText = true
        refilter()
        return true
    }

    /** reflect a vote locally right away */
    fun updatePost(post: Post) {
        val i = posts.indexOfFirst { it.id == post.id }
        if (i >= 0) posts[i] = post
        val j = loaded.indexOfFirst { it.id == post.id }
        if (j >= 0) loaded[j] = post
    }

    private companion object {
        // keep fetching pages until at least this many posts are showing (or the feed ends)
        const val MIN_SHOWN = 12
        // most pages fetched in a row without enough to show, before waiting for "Load more"
        const val MAX_PAGES = 3
        // posts loaded before judging whether a feed is mostly text (a page is 25)
        const val MIN_SAMPLE = 20
        // below this share of posts with media, a feed shows its text posts too
        const val MIN_MEDIA_SHARE = 0.25
        // top's time ranges, paged in turn as each runs out
        val TOP_RANGES = listOf("day", "week", "month", "year", "all")
    }
}

@Composable
fun rememberFeedState(feed: Feed, sort: FeedSort): FeedState {
    val scope = rememberCoroutineScope()
    val state = remember(feed, sort) { FeedState(feed, sort, scope).also { it.loadMore() } }
    val hidePinned by SettingsManager.getBoolAsState(AppPref.HIDE_PINNED)
    val hideNoMedia by SettingsManager.getBoolAsState(AppPref.HIDE_NO_MEDIA)
    LaunchedEffect(state, hidePinned, hideNoMedia) { state.setFilters(hidePinned, hideNoMedia) }
    return state
}
