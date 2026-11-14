package com.jpage4500.reddittv.manager

import com.jpage4500.reddittv.data.*
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.Utils
import kotlin.time.Instant

private val log = LoggerConfig.createLogger("RedditRss")

/**
 * signed out, reddit turns `.json` away from most networks (a 403 "blocked by network security")
 * and old.reddit.com sends a signed-out visitor to its login page. its RSS (Atom) feeds still
 * answer: any listing's `<path>/.rss`, and a post's, which lists the post then its comments.
 *
 * a feed entry has the title, author, community, time, a thumbnail and the post's `[link]` — no
 * score, comment count or media detail. counts come back as [Post.UNKNOWN_COUNT]; the link says
 * the media: i.redd.it is an image, v.redd.it a video (its HLS playlist needs no token), a link
 * back to the post a text post. comments come flat (no reply depth) and without scores
 */
object RedditRss {
    private val ENTRY = Regex("""<entry>([\s\S]*?)</entry>""")
    private val ID = Regex("""<id>([^<]*)</id>""")
    private val TITLE = Regex("""<title>([\s\S]*?)</title>""")
    private val AUTHOR = Regex("""<author>\s*<name>([^<]*)</name>""")
    private val CATEGORY = Regex("""<category\s+term="([^"]*)"""")
    private val LINK = Regex("""<link\s+href="([^"]*)"""")
    private val PUBLISHED = Regex("""<published>([^<]*)</published>""")
    private val UPDATED = Regex("""<updated>([^<]*)</updated>""")
    private val THUMBNAIL = Regex("""<media:thumbnail\s+url="([^"]*)"""")
    private val CONTENT = Regex("""<content[^>]*>([\s\S]*?)</content>""")

    // inside the decoded content
    private val POST_LINK = Regex("""<a href="([^"]*)">\[link\]</a>""")
    private val MARKDOWN = Regex("""<div class="md">([\s\S]*?)</div>\s*<!-- SC_ON -->""")
    private val V_REDD_IT = Regex("""^https?://v\.redd\.it/([A-Za-z0-9]+)""")
    private val GALLERY = Regex("""^https?://(?:www\.)?reddit\.com/gallery/""")
    private val IMAGE_EXT = Regex("""\.(jpe?g|png|gif|webp)(\?|$)""", RegexOption.IGNORE_CASE)

    private val BREAK = Regex("""<br\s*/?>|</p>|</li>|</h\d>|</blockquote>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[^>]+>""")
    private val BLANK_LINES = Regex("""\n{3,}""")

    /** a listing's feed; [limit] is what was asked for, so a full page has more after it */
    fun parseListing(body: String, limit: Int): Listing {
        val posts = ENTRY.findAll(body).mapNotNull { parsePostSafe(it.groupValues[1]) }.toList()
        log.d("parseListing: ${posts.size} posts")
        return Listing(posts, after = if (posts.size >= limit) posts.last().fullName else null)
    }

    /** a post's feed: its first entry is the post (t3_), the rest its comments (t1_) */
    fun parsePostDetail(body: String): PostDetail? {
        val entries = ENTRY.findAll(body).map { it.groupValues[1] }.toList()
        val postEntry = entries.firstOrNull { field(ID, it)?.startsWith("t3_") == true } ?: return null
        val post = parsePostSafe(postEntry) ?: return null
        val comments = entries.filter { field(ID, it)?.startsWith("t1_") == true }.mapNotNull { parseComment(it, post.author) }
        log.d("parsePostDetail: ${post.id}, ${comments.size} comments")
        return PostDetail(post, comments)
    }

    private fun parsePostSafe(entry: String): Post? = try {
        parsePost(entry)
    } catch (e: Exception) {
        log.w("parsePostSafe: ${e.message}")
        null
    }

    private fun parsePost(entry: String): Post? {
        val fullName = field(ID, entry)?.takeIf { it.startsWith("t3_") } ?: return null
        val id = fullName.removePrefix("t3_")
        val content = content(entry) ?: ""
        val link = POST_LINK.find(content)?.groupValues?.get(1)?.let { Utils.decodeHtmlEntities(it) }
        val permalink = field(LINK, entry)?.let { pathOf(it) } ?: "/comments/$id/"
        val thumbnail = field(THUMBNAIL, entry)
        val selfText = MARKDOWN.find(content)?.groupValues?.get(1)?.let { htmlToText(it) } ?: ""
        val url = link ?: "https://www.reddit.com$permalink"
        return Post(
            id = id,
            fullName = fullName,
            title = field(TITLE, entry) ?: "",
            author = author(entry),
            subreddit = field(CATEGORY, entry) ?: "",
            score = Post.UNKNOWN_COUNT,
            numComments = Post.UNKNOWN_COUNT,
            createdUtc = time(entry),
            permalink = permalink,
            url = url,
            domain = url.substringAfter("://").substringBefore('/'),
            selfText = selfText,
            isNsfw = false,
            isSpoiler = false,
            isStickied = false,
            flair = null,
            likes = null,
            media = media(link, id, thumbnail),
        )
    }

    private fun media(link: String?, id: String, thumbnail: String?): PostMedia {
        // a text post's [link] is the post itself
        if (link == null || link.contains("/comments/$id")) return PostMedia.None
        V_REDD_IT.find(link)?.let {
            return PostMedia.Video(
                url = "https://v.redd.it/${it.groupValues[1]}/HLSPlaylist.m3u8",
                mimeType = "application/x-mpegURL",
                posterUrl = thumbnail,
                width = 0,
                height = 0,
                durationSec = 0,
            )
        }
        if (IMAGE_EXT.containsMatchIn(link)) {
            return PostMedia.Image(url = link, previewUrl = thumbnail ?: link, width = 0, height = 0, isGif = link.contains(".gif", ignoreCase = true))
        }
        // a gallery's images aren't in the feed: its cover is all there is
        if (GALLERY.containsMatchIn(link) && thumbnail != null) {
            return PostMedia.Image(url = thumbnail, previewUrl = thumbnail, width = 0, height = 0)
        }
        return PostMedia.Link(link, thumbnail)
    }

    private fun parseComment(entry: String, op: String): Comment? {
        val fullName = field(ID, entry) ?: return null
        val content = content(entry) ?: return null
        val author = author(entry)
        return Comment(
            id = fullName.removePrefix("t1_"),
            fullName = fullName,
            author = author,
            body = htmlToText(MARKDOWN.find(content)?.groupValues?.get(1) ?: content),
            score = Post.UNKNOWN_COUNT,
            createdUtc = time(entry),
            depth = 0,
            isSubmitter = author.isNotEmpty() && author == op,
            likes = null,
        )
    }

    // the entry's HTML: escaped once inside the XML
    private fun content(entry: String): String? = field(CONTENT, entry)

    /** the first group of [regex] in [entry], entities decoded */
    private fun field(regex: Regex, entry: String): String? =
        regex.find(entry)?.groupValues?.get(1)?.trim()?.let { Utils.decodeHtmlEntities(it) }

    // "/u/name" → "name"
    private fun author(entry: String): String = field(AUTHOR, entry)?.removePrefix("/u/") ?: ""

    // seconds, as the .json view's created_utc
    private fun time(entry: String): Long {
        val text = field(PUBLISHED, entry) ?: field(UPDATED, entry) ?: return 0
        return runCatching { Instant.parse(text).epochSeconds }.getOrDefault(0)
    }

    // "https://www.reddit.com/r/x/comments/…/" → "/r/x/comments/…/"
    private fun pathOf(url: String): String = "/" + url.substringAfter("://").substringAfter('/')

    /** reddit's rendered markdown, as plain text with its paragraphs */
    private fun htmlToText(html: String): String {
        val text = html.replace(BREAK, "\n").replace(TAG, "")
        return Utils.decodeHtmlEntities(text).replace(BLANK_LINES, "\n\n").trim()
    }
}
