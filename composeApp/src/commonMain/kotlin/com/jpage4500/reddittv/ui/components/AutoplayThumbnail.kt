package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.data.AppPref
import com.jpage4500.reddittv.data.Post
import com.jpage4500.reddittv.data.PostMedia
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.util.SettingsManager
import kotlinx.coroutines.delay

// how long a video post stays active before it starts loading — long enough to skip the tiles a
// held arrow key (or a fling) passes over — and before it plays
private const val PRELOAD_DELAY_MS = 150L
private const val AUTOPLAY_DELAY_MS = 600L

/**
 * a post's still ([PostThumbnail]) that — for a video, while [isActive] (the focused tile, the
 * phone list's main post) — plays the video over it, muted, once it's held that a moment. not when
 * it's blurred (NSFW / spoiler) or autoplay is off. it starts loading, paused, before that, so
 * it's ready to play by the time it should
 */
@Composable
fun AutoplayThumbnail(
    post: Post,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    textColor: Color = Colors.TextDim,
) {
    val video = post.media as? PostMedia.Video
    val autoplay by SettingsManager.getBoolAsState(AppPref.AUTOPLAY_VIDEO)
    val showNsfw by SettingsManager.getBoolAsState(AppPref.SHOW_NSFW)
    val isHidden = (post.isNsfw && !showNsfw) || post.isSpoiler
    var isLoadingPreview by remember(post.id) { mutableStateOf(false) }
    var isPreviewing by remember(post.id) { mutableStateOf(false) }
    LaunchedEffect(isActive, video, autoplay, isHidden) {
        isLoadingPreview = false
        isPreviewing = false
        if (isActive && video != null && autoplay && !isHidden) {
            delay(PRELOAD_DELAY_MS)
            isLoadingPreview = true
            delay(AUTOPLAY_DELAY_MS - PRELOAD_DELAY_MS)
            isPreviewing = true
        }
    }
    // until the player draws its first frame the still stays up, with a spinner over it
    var isVideoShowing by remember(post.id) { mutableStateOf(false) }
    LaunchedEffect(isLoadingPreview) { if (!isLoadingPreview) isVideoShowing = false }
    val previewControl = rememberVideoControl(post.id)

    Box(modifier) {
        PostThumbnail(
            post = post,
            modifier = Modifier.fillMaxSize(),
            textColor = textColor,
            showVideoBadge = !isPreviewing,
        )
        if (isLoadingPreview && video != null) {
            VideoPreview(
                url = video.previewSource,
                headers = REDDIT_MEDIA_HEADERS,
                mimeType = video.previewMimeType,
                // hidden, not left out: it has to be laid out to load and play
                modifier = Modifier.matchParentSize().alpha(if (isPreviewing && isVideoShowing) 1f else 0f),
                // never shown: it keeps the player's controls and focus out of the tile
                control = previewControl,
                isPreview = true,
                play = isPreviewing,
                onFirstFrame = { isVideoShowing = true },
            )
            if (isPreviewing && !isVideoShowing) {
                CircularProgressIndicator(
                    color = Colors.Accent,
                    strokeWidth = 3.dp,
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                )
            }
        }
    }
}
