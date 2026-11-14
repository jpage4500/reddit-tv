package com.jpage4500.reddittv.data

/**
 * a post, parsed from a listing's `t3` child (see RedditParser). only what the UI shows is kept
 */
data class Post(
    val id: String,
    // "t3_<id>": what /api/vote and /api/save take
    val fullName: String,
    val title: String,
    val author: String,
    val subreddit: String,
    val score: Int,
    val numComments: Int,
    val createdUtc: Long,
    val permalink: String,
    val url: String,
    val domain: String,
    val selfText: String,
    val isNsfw: Boolean,
    val isSpoiler: Boolean,
    val isStickied: Boolean,
    val flair: String?,
    // true = upvoted, false = downvoted, null = no vote
    val likes: Boolean?,
    val media: PostMedia,
) {
    val isText: Boolean get() = media is PostMedia.None

    /** false for a text post, or a link with no thumbnail: nothing to look at in a tile */
    val hasMedia: Boolean get() = media !is PostMedia.None && !(media is PostMedia.Link && media.thumbnailUrl == null)

    companion object {
        // a score or comment count reddit didn't give (signed out, read from RSS): not shown
        const val UNKNOWN_COUNT = -1
    }
}

/** what a post shows in its tile and at the top of the post screen */
sealed class PostMedia {
    data object None : PostMedia()

    data class Image(
        val url: String,
        // a smaller copy for the grid; same as url when reddit gave none
        val previewUrl: String,
        val width: Int,
        val height: Int,
        val isGif: Boolean = false,
    ) : PostMedia()

    data class Gallery(val images: List<Image>) : PostMedia()

    data class Video(
        // v.redd.it: the HLS playlist (has audio); others: an mp4
        val url: String,
        val mimeType: String?,
        val posterUrl: String?,
        val width: Int,
        val height: Int,
        val durationSec: Int,
        // v.redd.it: the mp4 with the picture only — one request to start, where the playlist takes
        // several. what a muted tile plays. null when there's only [url]
        val previewUrl: String? = null,
    ) : PostMedia() {
        /** what a muted preview plays, and its type */
        val previewSource: String get() = previewUrl ?: url
        val previewMimeType: String? get() = if (previewUrl != null) "video/mp4" else mimeType
    }

    /** an outside link with an optional thumbnail */
    data class Link(val url: String, val thumbnailUrl: String?) : PostMedia()

    /** the grid's still for this post, or null */
    val thumbnail: String?
        get() = when (this) {
            is Image -> previewUrl
            is Gallery -> images.firstOrNull()?.previewUrl
            is Video -> posterUrl
            is Link -> thumbnailUrl
            None -> null
        }

    /** width / height of the media, or null when unknown */
    val aspectRatio: Float?
        get() = when (this) {
            is Image -> if (width > 0 && height > 0) width.toFloat() / height else null
            is Gallery -> images.firstOrNull()?.aspectRatio
            is Video -> if (width > 0 && height > 0) width.toFloat() / height else null
            else -> null
        }
}

/**
 * a comment, flattened from the reply tree: [depth] 0 is top level. a "more" stub becomes a
 * [Comment] with [isMore] true and [moreCount] replies behind it
 */
data class Comment(
    val id: String,
    val fullName: String,
    val author: String,
    val body: String,
    val score: Int,
    val createdUtc: Long,
    val depth: Int,
    val isSubmitter: Boolean,
    val likes: Boolean?,
    val isMore: Boolean = false,
    val moreCount: Int = 0,
)

/** one page of a feed; [after] is the cursor for the next, null at the end */
data class Listing(
    val posts: List<Post>,
    val after: String?,
)

/** a post and its comments: /comments/<id>.json */
data class PostDetail(
    val post: Post,
    val comments: List<Comment>,
)

/** a subreddit for the sidebar and search */
data class Subreddit(
    val name: String,
    val title: String,
    val iconUrl: String?,
    val subscribers: Int,
)

/** the signed-in user: /api/me.json. [modHash] goes with every vote and save */
data class RedditUser(
    val name: String,
    val modHash: String,
    val karma: Int,
    val iconUrl: String?,
)

/**
 * where a feed comes from: home ("" path), a subreddit ("/r/space"), popular, all, or a user.
 * [path] is what's saved in [AppPref.LAST_FEED]
 */
data class Feed(val path: String) {
    val isHome: Boolean get() = path.isEmpty()

    /** label for the header: "r/space"; empty for home, whose label is a string resource (feedLabel) */
    val label: String
        get() = path.trimStart('/')

    /** the community this is the feed of ("space"), or null: home, popular, all, a user */
    val subredditName: String?
        get() = path.takeIf { it.startsWith("/r/") && this != POPULAR && this != ALL }?.removePrefix("/r/")?.takeIf { it.isNotEmpty() && '/' !in it }

    companion object {
        val HOME = Feed("")
        val POPULAR = Feed("/r/popular")
        val ALL = Feed("/r/all")

        fun subreddit(name: String) = Feed("/r/${name.removePrefix("r/")}")
    }
}
