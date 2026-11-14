package com.jpage4500.reddittv.ui.components

import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import com.jpage4500.reddittv.R
import com.jpage4500.reddittv.data.AppPref
import com.jpage4500.reddittv.util.SettingsManager
import org.jetbrains.compose.resources.stringResource
import reddittv.composeapp.generated.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.PlatformUtils

private val log = LoggerConfig.createLogger("VideoPreview")

/**
 * Media3's player and its own controls, whose full screen button moves the video to a window of
 * its own over the whole screen — turned on its side for a video wider than it's tall. back, or
 * the button again, returns it to where it was
 */
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
    val context = LocalContext.current
    val player = remember(url) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
        headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.let { httpFactory.setUserAgent(it.value) }
        // a tile is ~230dp wide: a smaller picture downloads its first segment sooner
        val trackSelector = DefaultTrackSelector(context).apply {
            if (isPreview) parameters = buildUponParameters().setMaxVideoSize(PREVIEW_MAX_WIDTH, PREVIEW_MAX_HEIGHT).build()
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory)))
            .setTrackSelector(trackSelector)
            .setLoadControl(startQuicklyLoadControl())
            .build()
            .apply {
                setMediaItem(MediaItem.Builder().setUri(url).setMimeType(mimeType).build())
                playWhenReady = play
                prepare()
            }
    }
    // paused, it still loads and shows its first frame: playing then starts at once
    LaunchedEffect(player, play) { player.playWhenReady = play }
    // false for a live stream, which has nothing to skip through
    var canSeek by remember(player) { mutableStateOf(player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) }
    // false while paused or ended
    var isPlaying by remember(player) { mutableStateOf(player.playWhenReady) }
    val isMutedSetting by SettingsManager.getBoolAsState(AppPref.VIDEO_MUTED)
    val isMuted = isPreview || isMutedSetting
    LaunchedEffect(player, isMuted) { player.volume = if (isMuted) 0f else 1f }
    val firstFrame by rememberUpdatedState(onFirstFrame)
    val error by rememberUpdatedState(onError)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrame?.invoke()
            }

            override fun onPlayerError(e: PlaybackException) {
                log.e("onPlayerError: ${e.errorCodeName}: ${e.message}")
                error?.invoke()
            }

            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                canSeek = availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = playWhenReady && player.playbackState != Player.STATE_ENDED
                control?.isPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = player.playWhenReady && playbackState != Player.STATE_ENDED
                control?.isPlaying = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    var isFullScreen by remember { mutableStateOf(false) }
    if (control != null) {
        LaunchedEffect(control, player) {
            control.commands.collect { command ->
                log.d("VideoPreview: $command")
                when (command) {
                    VideoCommand.TOGGLE_PLAY -> when {
                        // ended: OK plays it again from the start
                        player.playbackState == Player.STATE_ENDED -> {
                            player.seekTo(0)
                            player.play()
                        }
                        player.playWhenReady -> player.pause()
                        else -> player.play()
                    }
                    VideoCommand.FULL_SCREEN -> isFullScreen = true
                }
            }
        }
    }

    // one view per place it's shown: only the one on screen holds the player
    PlayerSurface(
        player = player.takeUnless { isFullScreen },
        canSeek = canSeek,
        isFullScreen = false,
        // driven from outside on a TV: its controls can't be reached, so they aren't shown
        isControlled = isPreview || (control != null && PlatformUtils.isTv()),
        // a tile shows its own spinner, over its still
        showBuffering = !isPreview,
        isPlaying = isPlaying,
        isMuted = isMuted,
        onFullScreen = { isFullScreen = it },
        onControls = {},
        modifier = modifier,
    )
    if (isFullScreen) {
        Dialog(
            onDismissRequest = { isFullScreen = false },
            // under the status and navigation bars too, which FullScreenWindow hides, as it does
            // the dialog's own margins
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            FullScreenWindow(isLandscape = player.videoSize.let { it.width > it.height })
            val isTv = PlatformUtils.isTv()
            // on a phone the player's controls start out showing; on a TV only a pause shows them
            var isControlsVisible by remember { mutableStateOf(!isTv) }
            // on a TV, while it plays, the remote's keys come here: the controls are hidden and
            // can't take focus. paused, the controls have it
            val focusRequester = remember { FocusRequester() }
            if (isTv) {
                LaunchedEffect(isPlaying) { if (isPlaying) runCatching { focusRequester.requestFocus() } }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .focusRequester(focusRequester)
                    .focusable(enabled = isTv)
                    .onKeyEvent { event ->
                        if (!isTv || !isPlaying || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.MediaPlayPause -> player.pause()
                            Key.DirectionRight -> toggleVideoMuted()
                            else -> return@onKeyEvent false
                        }
                        true
                    },
            ) {
                PlayerSurface(
                    player = player,
                    canSeek = canSeek,
                    isFullScreen = true,
                    isControlled = false,
                    showBuffering = true,
                    isPlaying = isPlaying,
                    isMuted = isMuted,
                    onFullScreen = { isFullScreen = it },
                    onControls = { isControlsVisible = it },
                    modifier = Modifier.fillMaxSize(),
                )
                fullScreenOverlay?.invoke(this, isControlsVisible)
            }
        }
    }
}

/**
 * a PlayerView showing [player], or nothing while another one has it. [onFullScreen] is its full
 * screen button, with what it's asking for. the buttons that would do nothing aren't shown: previous
 * and next, and rewind and fast forward unless it [canSeek]. [onControls] says when its controls
 * show and hide. [isControlled]: no controls and no focus — the screen around it has the remote.
 *
 * it has a mute button ([isMuted]), and full screen on a TV its controls only show while paused:
 * playing, they're hidden and can't be woken by the remote
 */
@Composable
private fun PlayerSurface(
    player: Player?,
    canSeek: Boolean,
    isFullScreen: Boolean,
    isControlled: Boolean,
    showBuffering: Boolean,
    isPlaying: Boolean,
    isMuted: Boolean,
    onFullScreen: (Boolean) -> Unit,
    onControls: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val onToggle by rememberUpdatedState(onFullScreen)
    val onControlsChanged by rememberUpdatedState(onControls)
    val muteLabel = stringResource(if (isMuted) Res.string.video_unmute else Res.string.video_mute)
    AndroidView(
        factory = { ctx ->
            // inline, a TextureView: it scales and clips with what it's in (a growing grid tile,
            // rounded corners), where a SurfaceView is a hole in the window that ignores both
            val view = if (isFullScreen) PlayerView(ctx) else LayoutInflater.from(ctx).inflate(R.layout.player_texture_view, null) as PlayerView
            view.apply {
                // before it's given a player: setting one with the controller on shows the
                // controls, which focus their play button — pulling the remote's focus out of a
                // grid tile, and once that button is blocked, on to the first thing on screen
                if (isControlled) {
                    useController = false
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
                // the still behind it shows until the first frame
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setShowBuffering(if (showBuffering) PlayerView.SHOW_BUFFERING_ALWAYS else PlayerView.SHOW_BUFFERING_NEVER)
                // there's only ever the one video: no previous or next to go to
                setShowPreviousButton(false)
                setShowNextButton(false)
                // setting a listener is what shows the button. it's also told of the state set in
                // update below, which isn't a press: only a change from what this view shows is
                setFullscreenButtonClickListener { wanted -> if (wanted != isFullScreen) onToggle(wanted) }
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { onControlsChanged(it == View.VISIBLE) })
                // inline too: there its controls are the only way to it on a phone
                addMuteButton(this)
                if (isFullScreen) {
                    if (PlatformUtils.isTv()) {
                        controllerAutoShow = false
                        // paused, they stay up until it plays again
                        controllerShowTimeoutMs = 0
                    }
                }
            }
        },
        update = {
            // a TV full screen: the controls only while paused. set before the player, which
            // would otherwise show (and focus) the controls on the way in
            val hideControls = isControlled || (isFullScreen && PlatformUtils.isTv() && isPlaying)
            val wasHidden = !it.useController
            it.useController = !hideControls
            it.isFocusable = !hideControls
            it.descendantFocusability = if (hideControls) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
            it.player = player
            // the button flips its own icon when pressed: this view only ever shows the one
            it.setFullscreenButtonState(isFullScreen)
            // hidden rather than greyed out
            it.setShowRewindButton(canSeek)
            it.setShowFastForwardButton(canSeek)
            it.findViewWithTag<ImageButton>(MUTE_BUTTON_TAG)?.let { button ->
                button.setImageResource(if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on)
                button.contentDescription = muteLabel
            }
            if (isFullScreen && PlatformUtils.isTv() && !isPlaying && wasHidden) {
                it.showController()
                // OK on it plays again
                it.post { it.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus() }
            }
        },
        modifier = modifier,
    )
}

private const val MUTE_BUTTON_TAG = "mute"

// a tile's preview needs no more than 480p
private const val PREVIEW_MAX_WIDTH = 854
private const val PREVIEW_MAX_HEIGHT = 480
// ms of video buffered before it starts (and after a stall): Media3's defaults are 2.5s and 5s
private const val START_BUFFER_MS = 750
private const val REBUFFER_MS = 2_000

/** starts playing with less buffered than Media3's defaults; the rest are unchanged */
private fun startQuicklyLoadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
        DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
        START_BUFFER_MS,
        REBUFFER_MS,
    )
    .build()

/**
 * a mute button in the player's bottom bar, before its full screen button and styled like it.
 * its icon follows the setting (see PlayerSurface's update)
 */
private fun addMuteButton(view: PlayerView) {
    val bar = view.findViewById<ViewGroup>(androidx.media3.ui.R.id.exo_basic_controls)
    if (bar == null) {
        log.w("addMuteButton: no exo_basic_controls")
        return
    }
    val button = ImageButton(view.context, null, 0, androidx.media3.ui.R.style.ExoStyledControls_Button_Bottom).apply {
        tag = MUTE_BUTTON_TAG
        setOnClickListener { toggleVideoMuted() }
    }
    val fullScreen = bar.findViewById<View>(androidx.media3.ui.R.id.exo_fullscreen)
    val index = fullScreen?.let { bar.indexOfChild(it) } ?: bar.childCount
    bar.addView(button, index, fullScreen?.layoutParams ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
}

/**
 * makes the dialog's window the whole screen, edge to edge and black: no margin, no dimmed app
 * behind it, and none of the system's bars — it has its own window, so hiding the activity's does
 * nothing. the screen stays on while it's up. with [isLandscape] it holds the screen on its side, which is put back on leaving
 */
@Composable
private fun FullScreenWindow(isLandscape: Boolean) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window == null) log.w("FullScreenWindow: no dialog window")
        window?.let {
            // a dialog's background is what insets it from the screen's edges
            it.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            it.setDimAmount(0f)
            // this window's, not the activity's behind it: gone with the dialog
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // around a camera cutout too, which landscape puts on a short edge
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                it.attributes = it.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            val controller = WindowCompat.getInsetsController(it, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        // a TV is already on its side, and has no other way up
        val activity = PlatformUtils.screenActivity.takeIf { isLandscape && !PlatformUtils.isTv() }
        val orientationBefore = activity?.requestedOrientation
        log.d("FullScreenWindow: isLandscape=$isLandscape")
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (activity != null && orientationBefore != null) activity.requestedOrientation = orientationBefore
        }
    }
}
