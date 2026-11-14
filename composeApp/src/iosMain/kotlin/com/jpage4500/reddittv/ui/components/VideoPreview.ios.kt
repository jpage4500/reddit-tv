package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.currentItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.muted
import com.jpage4500.reddittv.data.AppPref
import com.jpage4500.reddittv.util.SettingsManager
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL
import kotlinx.coroutines.delay

// not in Apple's public headers, but it's the key every iOS player uses to send request headers
private const val HTTP_HEADERS_KEY = "AVURLAssetHTTPHeaderFieldsKey"
// how often it checks AVKit for its first picture, or a failure
private const val FIRST_FRAME_POLL_MS = 100L

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun VideoPreview(
    url: String,
    headers: Map<String, String>,
    mimeType: String?,
    modifier: Modifier,
    fullScreenOverlay: (@Composable BoxScope.(isControlsVisible: Boolean) -> Unit)?,
    control: VideoControl?,
    isPreview: Boolean,
    play: Boolean,
    onFirstFrame: (() -> Unit)?,
    onError: (() -> Unit)?,
) {
    var controller by remember(url) { mutableStateOf<AVPlayerViewController?>(null) }
    if (control != null) {
        LaunchedEffect(control, controller) {
            val player = controller?.player ?: return@LaunchedEffect
            control.commands.collect { command ->
                // full screen is the system player's own button: AVKit has no public call for it
                if (command != VideoCommand.TOGGLE_PLAY) return@collect
                if (player.rate == 0f) player.play() else player.pause()
                control.isPlaying = player.rate != 0f
            }
        }
    }
    // the system player's own mute button changes it too, but only for this video
    val isMutedSetting by SettingsManager.getBoolAsState(AppPref.VIDEO_MUTED)
    val isMuted = isPreview || isMutedSetting
    LaunchedEffect(controller, isMuted) { controller?.player?.muted = isMuted }
    // paused, it still loads its first frame: playing then starts at once
    LaunchedEffect(controller, play) {
        val player = controller?.player ?: return@LaunchedEffect
        if (play) player.play() else player.pause()
    }
    // AVKit only tells through KVO: poll until the first picture is ready to show
    if (onFirstFrame != null) {
        val firstFrame by rememberUpdatedState(onFirstFrame)
        LaunchedEffect(controller) {
            val current = controller ?: return@LaunchedEffect
            while (!current.readyForDisplay) delay(FIRST_FRAME_POLL_MS)
            firstFrame()
        }
    }
    // the same, for a stream it can't play
    if (onError != null) {
        val error by rememberUpdatedState(onError)
        LaunchedEffect(controller) {
            val item = controller?.player?.currentItem ?: return@LaunchedEffect
            while (item.status != AVPlayerItemStatusFailed) delay(FIRST_FRAME_POLL_MS)
            error()
        }
    }
    UIKitViewController(
        factory = {
            AVPlayerViewController().apply {
                controller = this
                // a preview is only a picture: no controls, and a tap goes to the card under it
                if (isPreview) showsPlaybackControls = false
                val nsUrl = NSURL.URLWithString(url)
                if (nsUrl != null) {
                    val asset = AVURLAsset(uRL = nsUrl, options = mapOf<Any?, Any?>(HTTP_HEADERS_KEY to headers))
                    player = AVPlayer(playerItem = AVPlayerItem(asset = asset))
                    if (play) player?.play()
                }
            }
        },
        modifier = modifier,
        properties = if (isPreview) UIKitInteropProperties(interactionMode = null) else UIKitInteropProperties(),
        onRelease = { it.player?.pause() },
    )
}
