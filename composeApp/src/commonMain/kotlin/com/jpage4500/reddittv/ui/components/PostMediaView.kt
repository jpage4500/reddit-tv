package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.window.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpage4500.reddittv.data.AppPref
import com.jpage4500.reddittv.data.Post
import com.jpage4500.reddittv.data.PostMedia
import com.jpage4500.reddittv.manager.LinkPreview
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.util.RedditFormat
import com.jpage4500.reddittv.util.DateUtils
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.SettingsManager
import reddittv.composeapp.generated.resources.*
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.*

/** headers for i.redd.it / v.redd.it: reddit's CDN answers 403 to a request with no referer */
val REDDIT_MEDIA_HEADERS = mapOf("Referer" to "https://www.reddit.com/")

private val log = LoggerConfig.createLogger("PostMediaView")

/**
 * a post's still for a tile or card: the image (or a gallery's [galleryIndex]), a video's poster
 * with a play badge, a link's thumbnail. a text post shows the start of its text instead.
 * NSFW media is blurred unless [AppPref.SHOW_NSFW] is on
 */
@Composable
fun PostThumbnail(
    post: Post,
    modifier: Modifier = Modifier,
    galleryIndex: Int = 0,
    contentScale: ContentScale = ContentScale.Crop,
    textColor: Color = Colors.TextDim,
    textSize: Int = 11,
    showVideoBadge: Boolean = true,
) {
    val showNsfw by SettingsManager.getBoolAsState(AppPref.SHOW_NSFW)
    val media = post.media
    Box(modifier = modifier.background(Colors.MediaBackground), contentAlignment = Alignment.Center) {
        val url = when (media) {
            is PostMedia.Gallery -> media.images.getOrNull(galleryIndex)?.previewUrl
            else -> rememberMediaImage(media)
        }
        if (url != null) {
            val hide = (post.isNsfw && !showNsfw) || post.isSpoiler
            AppImage(
                url = url,
                modifier = Modifier.fillMaxSize().then(if (hide) Modifier.blur(24.dp) else Modifier),
                contentScale = contentScale,
            )
            if (hide) Badge(stringResource(if (post.isNsfw) Res.string.badge_nsfw else Res.string.badge_spoiler), Modifier.align(Alignment.Center), Colors.Nsfw)
        } else if (media is PostMedia.None && post.selfText.isNotBlank()) {
            Text(
                text = RedditFormat.oneLine(post.selfText),
                color = textColor,
                fontSize = textSize.sp,
                lineHeight = (textSize * 1.3f).sp,
                modifier = Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 11.dp),
                overflow = TextOverflow.Ellipsis,
            )
        } else if (media is PostMedia.Link) {
            Icon(imageVector = MetaIcons.ExternalLink, contentDescription = post.domain, tint = Colors.TextDim, modifier = Modifier.size(28.dp))
        } else {
            Text(text = post.domain, color = Colors.TextDim, fontSize = 12.sp)
        }

        when (media) {
            // small and in the corner: the picture is what matters. hidden while it plays
            is PostMedia.Video -> if (showVideoBadge) VideoBadge(media, Modifier.align(Alignment.BottomStart).padding(6.dp))
            is PostMedia.Gallery -> Badge(
                text = "${galleryIndex + 1}/${media.images.size}",
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
            is PostMedia.Image -> if (media.isGif) Badge(stringResource(Res.string.badge_gif), Modifier.align(Alignment.TopEnd).padding(6.dp))
            is PostMedia.Link -> if (url != null) LinkBadge(post.domain, Modifier.align(Alignment.BottomStart).padding(6.dp))
            PostMedia.None -> {}
        }
    }
}

/** a video's corner badge: a play icon and its length, when known */
@Composable
fun VideoBadge(video: PostMedia.Video, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Colors.MediaBadge)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        AppIcon(res = Res.drawable.icon_play, modifier = Modifier.size(10.dp), color = Colors.Text)
        if (video.durationSec > 0) {
            Text(
                text = DateUtils.formatDuration(video.durationSec * 1000L),
                color = Colors.Text,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/** a link post's corner badge: reddit's "opens elsewhere" arrow in a dark circle, no site name */
@Composable
fun LinkBadge(domain: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(22.dp).clip(CircleShape).background(Colors.MediaBadge),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = MetaIcons.ExternalLink, contentDescription = domain, tint = Colors.Text, modifier = Modifier.size(13.dp))
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Colors.Text) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Colors.MediaBadge)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * a post's media at full size, for the post screen: the whole image fits (no crop), a video
 * plays right away, a gallery steps with [galleryIndex]. not blurred: the user chose to open it.
 * [videoControl] lets the screen play/pause the video and take it full screen
 */
@Composable
fun PostMediaFull(
    post: Post,
    modifier: Modifier = Modifier,
    galleryIndex: Int = 0,
    videoControl: VideoControl? = null,
) {
    Box(modifier = modifier.background(Colors.MediaBackground), contentAlignment = Alignment.Center) {
        when (val media = post.media) {
            is PostMedia.Video -> FullVideo(media, videoControl)
            is PostMedia.Image -> AppImage(url = media.url, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            is PostMedia.Gallery -> {
                val image = media.images.getOrNull(galleryIndex) ?: media.images.first()
                AppImage(url = image.url, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Badge(
                    text = "${galleryIndex + 1} / ${media.images.size}",
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                )
            }
            is PostMedia.Link -> {
                AppImage(url = rememberMediaImage(media), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                LinkBadge(post.domain, Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
            PostMedia.None -> {}
        }
    }
}

/**
 * an image post's image (or a gallery's, starting at [startIndex]) over the whole screen, to zoom
 * and pan. left/right step a gallery; back, Escape or a tap closes it
 */
@Composable
fun FullScreenImage(
    post: Post,
    startIndex: Int,
    onIndexChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val images = when (val media = post.media) {
        is PostMedia.Image -> listOf(media)
        is PostMedia.Gallery -> media.images
        else -> emptyList()
    }
    if (images.isEmpty()) return
    var index by remember { mutableStateOf(startIndex.coerceIn(0, images.lastIndex)) }
    val focusRequester = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Colors.MediaBackground)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || images.size < 2) return@onKeyEvent false
                    val next = when (event.key) {
                        Key.DirectionRight -> (index + 1) % images.size
                        Key.DirectionLeft -> (index - 1 + images.size) % images.size
                        else -> return@onKeyEvent false
                    }
                    index = next
                    onIndexChanged(next)
                    true
                },
        ) {
            ZoomableImage(
                model = images[index].url,
                contentDescription = post.title,
                modifier = Modifier.fillMaxSize(),
                onTap = { onDismiss() },
            )
            if (images.size > 1) {
                Badge(
                    text = "${index + 1} / ${images.size}",
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                )
            }
        }
    }
}

/**
 * a post's video with its sound (the HLS playlist). when that won't play — reddit's CDN sometimes
 * refuses a playlist for a while — it plays the picture-only mp4 a tile uses instead, marked as
 * having no sound
 */
@Composable
private fun BoxScope.FullVideo(media: PostMedia.Video, videoControl: VideoControl?) {
    var isFallback by remember(media.url) { mutableStateOf(false) }
    val fallbackUrl = media.previewUrl
    VideoPreview(
        url = if (isFallback && fallbackUrl != null) fallbackUrl else media.url,
        headers = REDDIT_MEDIA_HEADERS,
        mimeType = if (isFallback) media.previewMimeType else media.mimeType,
        modifier = Modifier.fillMaxSize(),
        control = videoControl,
        onError = if (fallbackUrl != null && !isFallback) {
            {
                log.w("FullVideo: playlist failed, playing the mp4 without sound: ${media.url}")
                isFallback = true
            }
        } else {
            null
        },
    )
    if (isFallback) {
        Badge(
            text = stringResource(Res.string.video_no_sound),
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )
    }
}

/**
 * the picture for [media]: its own thumbnail, or — a link reddit gave none — the one its page
 * unfurls to, once [LinkPreview] has read it
 */
@Composable
fun rememberMediaImage(media: PostMedia): String? {
    val own = media.thumbnail
    if (own != null || media !is PostMedia.Link) return own
    val image by produceState(LinkPreview.cached(media.url), media.url) {
        value = LinkPreview.imageFor(media.url)
    }
    return image
}
