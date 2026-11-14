package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.jpage4500.reddittv.data.AppPref
import com.jpage4500.reddittv.util.SettingsManager
import kotlinx.coroutines.flow.*

/** how far the player's skip buttons and the remote's left/right jump */
const val SKIP_BACK_MS = 10_000L
const val SKIP_FORWARD_MS = 30_000L

/**
 * plays [url] on this device, sending [headers] with every request — so a preview fetches the
 * stream the same way the TV will, and fails the same way too.
 *
 * Android: Media3 PlayerView. iOS: AVPlayerViewController. desktop: FFmpeg (PreviewPlayer), with
 * open-in-browser when it can't play it.
 *
 * [fullScreenOverlay] is drawn over the video while it's full screen, told whether the player's
 * controls are showing. Android only: iOS's full screen is the system's own, and the desktop's
 * controls already fill the bottom
 *
 * [control], when given, lets the screen around it play/pause it and take it full screen — ie: the
 * remote's OK on the post screen. inline, the player then leaves the remote's focus to that screen.
 * [isPreview]: a grid tile's — without sound whatever [AppPref.VIDEO_MUTED] says, at a low
 * quality, and quick to start. [play] false loads it and holds on its first frame, so it can start
 * at once later. [onFirstFrame] is called once that first picture is ready, so a tile can keep its
 * still until then. [onError] is called when it can't be played, so the caller can try another
 * source; the player still shows its own failure
 */
@Composable
expect fun VideoPreview(
    url: String,
    headers: Map<String, String>,
    mimeType: String? = null,
    modifier: Modifier = Modifier,
    fullScreenOverlay: (@Composable BoxScope.(isControlsVisible: Boolean) -> Unit)? = null,
    control: VideoControl? = null,
    isPreview: Boolean = false,
    play: Boolean = true,
    onFirstFrame: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
)

enum class VideoCommand { TOGGLE_PLAY, FULL_SCREEN }

/**
 * drives a [VideoPreview] from outside it: [togglePlay] and [openFullScreen] send it a command, and
 * it reports back whether it [isPlaying]
 */
class VideoControl {
    private val _commands = MutableSharedFlow<VideoCommand>(extraBufferCapacity = 8)
    val commands: SharedFlow<VideoCommand> = _commands

    /** set by the player: false while paused or ended */
    var isPlaying by mutableStateOf(true)

    fun togglePlay() = _commands.tryEmit(VideoCommand.TOGGLE_PLAY)
    fun openFullScreen() = _commands.tryEmit(VideoCommand.FULL_SCREEN)
}

/** flips the saved [AppPref.VIDEO_MUTED], which every player follows */
fun toggleVideoMuted() {
    val muted = !SettingsManager.getBoolean(AppPref.VIDEO_MUTED)
    SettingsManager.setBoolean(AppPref.VIDEO_MUTED, muted)
}

@Composable
fun rememberVideoControl(key: Any?): VideoControl = remember(key) { VideoControl() }
