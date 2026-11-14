package com.jpage4500.reddittv.manager

import com.jpage4500.reddittv.data.*
import com.jpage4500.reddittv.util.LoggerConfig
import kotlinx.serialization.json.*

private val log = LoggerConfig.createLogger("RedditParser")

/**
 * turns reddit's `.json` responses into [Post] / [Comment]. read with JsonElement rather than
 * @Serializable classes: the shapes are big, loose and change often, and one odd field shouldn't
 * drop a whole page. every request adds raw_json=1, so text arrives un-escaped (no &amp;)
 */
object RedditParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseListing(body: String): Listing {
        val data = json.parseToJsonElement(body).jsonObject.obj("data") ?: return Listing(emptyList(), null)
        val posts = data.arr("children")?.mapNotNull { child ->
            val obj = child.jsonObject
            if (obj.str("kind") != "t3") return@mapNotNull null
            obj.obj("data")?.let { parsePostSafe(it) }
        } ?: emptyList()
        return Listing(posts, data.str("after"))
    }

    /** /comments/<id>.json: an array of [listing with the post, listing of comments] */
    fun parsePostDetail(body: String): PostDetail? {
        val root = json.parseToJsonElement(body).jsonArray
        val postData = root.getOrNull(0)?.jsonObject?.obj("data")?.arr("children")
            ?.firstOrNull()?.jsonObject?.obj("data") ?: return null
        val post = parsePostSafe(postData) ?: return null
        val comments = mutableListOf<Comment>()
        root.getOrNull(1)?.jsonObject?.obj("data")?.arr("children")?.forEach {
            addComment(it.jsonObject, post.author, comments)
        }
        return PostDetail(post, comments)
    }

    fun parseSubreddits(body: String): Pair<List<Subreddit>, String?> {
        val data = json.parseToJsonElement(body).jsonObject.obj("data") ?: return emptyList<Subreddit>() to null
        val subs = data.arr("children")?.mapNotNull { child ->
            val d = child.jsonObject.obj("data") ?: return@mapNotNull null
            Subreddit(
                name = d.str("display_name") ?: return@mapNotNull null,
                title = d.str("title") ?: "",
                iconUrl = (d.str("community_icon") ?: d.str("icon_img"))?.takeIf { it.startsWith("http") }?.substringBefore('?'),
                subscribers = d.int("subscribers"),
            )
        } ?: emptyList()
        return subs to data.str("after")
    }

    /** /r/<name>/about.json: the community's "t5_…" id, or null */
    fun parseSubredditFullName(body: String): String? =
        json.parseToJsonElement(body).jsonObject.obj("data")?.str("name")?.takeIf { it.startsWith("t5_") }

    fun parseMe(body: String): RedditUser? {
        val root = json.parseToJsonElement(body).jsonObject
        // /api/me.json wraps it in "data"; a signed-out session answers {}
        val d = root.obj("data") ?: root
        val name = d.str("name") ?: return null
        return RedditUser(
            name = name,
            modHash = d.str("modhash") ?: "",
            karma = d.int("total_karma"),
            iconUrl = d.str("icon_img")?.substringBefore('?'),
        )
    }

    private fun parsePostSafe(d: JsonObject): Post? = try {
        parsePost(d)
    } catch (e: Exception) {
        log.e("parsePostSafe: ${d.str("id")}: ${e.message}")
        null
    }

    private fun parsePost(d: JsonObject): Post {
        // a crosspost carries its media on the original
        val src = d.arr("crosspost_parent_list")?.firstOrNull()?.jsonObject ?: d
        return Post(
            id = d.str("id") ?: "",
            fullName = d.str("name") ?: "t3_${d.str("id")}",
            title = d.str("title") ?: "",
            author = d.str("author") ?: "[deleted]",
            subreddit = d.str("subreddit") ?: "",
            score = d.int("score"),
            numComments = d.int("num_comments"),
            createdUtc = d.long("created_utc"),
            permalink = d.str("permalink") ?: "",
            url = d.str("url_overridden_by_dest") ?: d.str("url") ?: "",
            domain = d.str("domain") ?: "",
            selfText = d.str("selftext") ?: "",
            isNsfw = d.bool("over_18"),
            isSpoiler = d.bool("spoiler"),
            isStickied = d.bool("stickied"),
            flair = d.str("link_flair_text")?.takeIf { it.isNotBlank() },
            likes = d["likes"]?.jsonPrimitive?.booleanOrNull,
            media = parseMedia(src),
        )
    }

    private fun parseMedia(d: JsonObject): PostMedia {
        // reddit-hosted video
        val redditVideo = d.obj("secure_media")?.obj("reddit_video") ?: d.obj("media")?.obj("reddit_video")
            ?: d.obj("preview")?.obj("reddit_video_preview")
        val poster = previewImage(d)
        if (redditVideo != null) {
            val hls = redditVideo.str("hls_url")
            val mp4 = redditVideo.str("fallback_url")
            return PostMedia.Video(
                url = hls ?: mp4 ?: "",
                mimeType = if (hls != null) "application/x-mpegURL" else "video/mp4",
                posterUrl = poster?.previewUrl,
                width = redditVideo.int("width"),
                height = redditVideo.int("height"),
                durationSec = redditVideo.int("duration"),
                previewUrl = mp4.takeIf { hls != null },
            )
        }

        // gallery: media_metadata keyed by id, ordered by gallery_data
        if (d.bool("is_gallery")) {
            val meta = d.obj("media_metadata")
            val images = d.obj("gallery_data")?.arr("items")?.mapNotNull { item ->
                val m = meta?.obj(item.jsonObject.str("media_id") ?: return@mapNotNull null) ?: return@mapNotNull null
                if (m.str("status") != "valid") return@mapNotNull null
                val s = m.obj("s") ?: return@mapNotNull null
                val full = s.str("u") ?: s.str("gif") ?: return@mapNotNull null
                // "p" is smallest first; ~640px is plenty for a tile
                val preview = m.arr("p")?.mapNotNull { it.jsonObject }?.lastOrNull { it.int("x") <= 960 }?.str("u") ?: full
                PostMedia.Image(full, preview, s.int("x"), s.int("y"), isGif = s.str("gif") != null)
            } ?: emptyList()
            if (images.isNotEmpty()) return PostMedia.Gallery(images)
        }

        val url = d.str("url_overridden_by_dest") ?: d.str("url") ?: ""
        val hint = d.str("post_hint")
        if (hint == "image" || IMAGE_EXT.containsMatchIn(url)) {
            val img = poster
            val isGif = url.endsWith(".gif", ignoreCase = true)
            return PostMedia.Image(
                url = if (isGif) (img?.url ?: url) else url,
                previewUrl = img?.previewUrl ?: url,
                width = img?.width ?: 0,
                height = img?.height ?: 0,
                isGif = isGif,
            )
        }
        if (d.bool("is_self")) return PostMedia.None
        // an embed (youtube…) carries its own still where reddit gave no preview
        val embedThumb = (d.obj("secure_media") ?: d.obj("media"))?.obj("oembed")?.str("thumbnail_url")
        val thumb = poster?.previewUrl ?: embedThumb ?: d.str("thumbnail")?.takeIf { it.startsWith("http") }
        return PostMedia.Link(url, thumb)
    }

    /** the post's preview image, with a ~960px copy for the grid */
    private fun previewImage(d: JsonObject): PostMedia.Image? {
        val image = d.obj("preview")?.arr("images")?.firstOrNull()?.jsonObject ?: return null
        // an animated gif's still is in "source"; its mp4 is under variants (left for later)
        val source = image.obj("source") ?: return null
        val url = source.str("url") ?: return null
        val preview = image.arr("resolutions")?.mapNotNull { it.jsonObject }?.lastOrNull { it.int("width") <= 960 }?.str("url") ?: url
        return PostMedia.Image(url, preview, source.int("width"), source.int("height"))
    }

    private fun addComment(child: JsonObject, op: String, out: MutableList<Comment>) {
        val d = child.obj("data") ?: return
        when (child.str("kind")) {
            "t1" -> {
                out += Comment(
                    id = d.str("id") ?: "",
                    fullName = d.str("name") ?: "",
                    author = d.str("author") ?: "[deleted]",
                    body = d.str("body") ?: "",
                    score = d.int("score"),
                    createdUtc = d.long("created_utc"),
                    depth = d.int("depth"),
                    isSubmitter = d.str("author") == op,
                    likes = d["likes"]?.jsonPrimitive?.booleanOrNull,
                )
                // "replies" is "" when there are none
                (d["replies"] as? JsonObject)?.obj("data")?.arr("children")?.forEach {
                    addComment(it.jsonObject, op, out)
                }
            }
            "more" -> {
                val count = d.int("count")
                if (count > 0) {
                    out += Comment(
                        id = d.str("id") ?: "",
                        fullName = d.str("name") ?: "",
                        author = "",
                        body = "",
                        score = 0,
                        createdUtc = 0,
                        depth = d.int("depth"),
                        isSubmitter = false,
                        likes = null,
                        isMore = true,
                        moreCount = count,
                    )
                }
            }
        }
    }

    private val IMAGE_EXT = Regex("""\.(jpe?g|png|gif|webp)(\?|$)""", RegexOption.IGNORE_CASE)

    // -- JsonObject helpers: null rather than throw on a missing or JsonNull field --
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0
    private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.doubleOrNull?.toLong() ?: 0L
    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
}
