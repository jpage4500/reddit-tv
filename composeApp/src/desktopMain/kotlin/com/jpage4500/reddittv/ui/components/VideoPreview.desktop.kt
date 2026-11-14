package com.jpage4500.reddittv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.WindowPlacement
import com.jpage4500.reddittv.data.AppPref
import com.jpage4500.reddittv.ui.dialog.LocalPreviewWindowState
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.util.DateUtils
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.PlatformUtils
import com.jpage4500.reddittv.util.PreviewPlayer
import com.jpage4500.reddittv.util.SettingsManager
import reddittv.composeapp.generated.resources.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

private val log = LoggerConfig.createLogger("VideoPreview")

// playing, the controls get out of the way this long after the mouse stops — as the TV's do
private const val CONTROLS_TIMEOUT_MS = 2_000L
// how often the controls read the player's position
private const val POLL_MS = 200L

/**
 * FFmpeg (PreviewPlayer) plays it in the window, with the TV player's controls over it: a seek bar,
 * skip back and forward (where it can seek), play/pause, mute and full screen. a click on the
 * video, space or Enter plays and pauses; the arrow keys skip; M mutes; F goes full screen and back,
 * and Escape leaves it. full screen, the controls show while paused or the mouse moves, not for a
 * key. if it can't play, the browser is one click away, though it won't send headers
 */
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
    var isStarted by remember(url) { mutableStateOf(false) }
    var isFailed by remember(url) { mutableStateOf(false) }
    var frame by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    val firstFrame by rememberUpdatedState(onFirstFrame)
    val error by rememberUpdatedState(onError)
    val player = remember(url) {
        PreviewPlayer(
            url = url,
            headers = headers,
            mimeType = mimeType,
            onStarted = { hasVideo ->
                isStarted = true
                // an audio-only stream never draws a frame: it's as started as it'll get
                if (!hasVideo) firstFrame?.invoke()
            },
            onFrame = {
                if (frame == null) firstFrame?.invoke()
                frame = it
            },
            onError = {
                isFailed = true
                error?.invoke()
            },
            isPreview = isPreview,
            startPaused = !play,
        )
    }
    LaunchedEffect(player, play) { if (play) player.play() else player.pause() }
    DisposableEffect(player) {
        player.start()
        onDispose { player.release() }
    }
    val isMutedSetting by SettingsManager.getBoolAsState(AppPref.VIDEO_MUTED)
    val isMuted = isPreview || isMutedSetting
    LaunchedEffect(player, isMuted) { player.isMuted = isMuted }

    // the player's state, as of the last poll
    var positionMs by remember(player) { mutableStateOf(0L) }
    var durationMs by remember(player) { mutableStateOf(0L) }
    var isPlaying by remember(player) { mutableStateOf(false) }
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.positionMs
            durationMs = player.durationMs
            isPlaying = !player.isPaused && !player.isEnded
            delay(POLL_MS)
        }
    }

    // where the seek bar's being dragged to — shown instead of the position until it's let go
    var dragMs by remember(player) { mutableStateOf<Long?>(null) }
    // bumped by anything the user does, which brings the controls back
    var activity by remember(player) { mutableStateOf(0) }
    var isControlsVisible by remember(player) { mutableStateOf(true) }
    LaunchedEffect(activity, isPlaying, dragMs) {
        isControlsVisible = true
        if (isPlaying && dragMs == null) {
            delay(CONTROLS_TIMEOUT_MS)
            isControlsVisible = false
        }
    }
    // in a window of its own (PreviewContainer), full screen is that window's. anywhere else the
    // player fills the app's window, and the window the screen
    val ownWindow = LocalPreviewWindowState.current
    var isAppFullScreen by remember { mutableStateOf(false) }
    val isFullScreen = if (ownWindow != null) ownWindow.placement == WindowPlacement.Fullscreen else isAppFullScreen
    // going full screen while it plays, the controls get out of the way at once
    LaunchedEffect(isFullScreen) { if (isFullScreen && isPlaying) isControlsVisible = false }
    // maximized or not, to go back to
    var placementBefore by remember { mutableStateOf(WindowPlacement.Floating) }
    fun toggleFullScreen() {
        log.d("toggleFullScreen: ${!isFullScreen}")
        when {
            ownWindow == null -> isAppFullScreen = !isAppFullScreen
            isFullScreen -> ownWindow.placement = placementBefore
            else -> {
                placementBefore = ownWindow.placement
                ownWindow.placement = WindowPlacement.Fullscreen
            }
        }
    }
    fun userAction(showControls: Boolean = true, action: () -> Unit) {
        if (showControls) activity++
        action()
        // show it now rather than at the next poll
        isPlaying = !player.isPaused && !player.isEnded
        positionMs = player.positionMs
    }

    if (control != null) {
        LaunchedEffect(isPlaying) { control.isPlaying = isPlaying }
        LaunchedEffect(control, player) {
            control.commands.collect { command ->
                log.d("VideoPreview: $command")
                when (command) {
                    VideoCommand.TOGGLE_PLAY -> if (isStarted && !isFailed) userAction(action = player::togglePlay)
                    VideoCommand.FULL_SCREEN -> if (!isFullScreen) userAction(showControls = false) { toggleFullScreen() }
                }
            }
        }
    }

    // the player and its controls, drawn in the dialog or over the whole window
    val surface: @Composable (Modifier) -> Unit = { surfaceModifier ->
        val focusRequester = remember { FocusRequester() }
        // driven from outside (control), inline it leaves the keyboard to the screen around it
        val takesFocus = control == null || isFullScreen
        LaunchedEffect(focusRequester, takesFocus) {
            if (takesFocus) runCatching { focusRequester.requestFocus() }
        }

        Box(
            modifier = surfaceModifier
                .background(Colors.PreviewBackground)
                .onPointerEvent(PointerEventType.Move) { activity++ }
                .focusRequester(focusRequester)
                .focusable(enabled = takesFocus)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !isStarted) return@onKeyEvent false
                    // full screen, the keyboard doesn't bring the controls back: a pause does
                    val show = !isFullScreen
                    when (event.key) {
                        Key.Spacebar, Key.Enter, Key.NumPadEnter -> userAction(show, player::togglePlay)
                        Key.DirectionLeft -> userAction(show) { player.seekBy(-SKIP_BACK_MS) }
                        Key.DirectionRight -> userAction(show) { player.seekBy(SKIP_FORWARD_MS) }
                        Key.M -> userAction(show, ::toggleVideoMuted)
                        Key.F -> userAction(show) { toggleFullScreen() }
                        else -> return@onKeyEvent false
                    }
                    true
                }
                // a preview leaves a click to the card it's in (it opens the post)
                .then(
                    if (isPreview) Modifier
                    else Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (isStarted && !isFailed) userAction(action = player::togglePlay)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val current = frame
            when {
                isFailed -> Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = stringResource(Res.string.preview_failed), color = Colors.DarkGray)
                    Spacer(modifier = Modifier.height(12.dp))
                    AppButton(
                        icon = Res.drawable.icon_play,
                        text = stringResource(Res.string.open_in_browser),
                        onClick = { PlatformUtils.openUrl(url) },
                    )
                }
                current != null -> Image(
                    bitmap = current,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                // an audio-only stream starts and never draws a frame
                !isStarted -> CircularProgressIndicator()
            }

            AnimatedVisibility(
                // a tile's preview is just the picture
                visible = !isPreview && isStarted && !isFailed && isControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Colors.PreviewControlsBackground)
                        // a click between the buttons isn't a click on the video
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { activity++ }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    val canSeek = durationMs > 0
                    if (canSeek) {
                        val shownMs = dragMs ?: positionMs
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TimeText(DateUtils.formatDuration(shownMs))
                            Slider(
                                value = shownMs.coerceIn(0, durationMs).toFloat(),
                                valueRange = 0f..durationMs.toFloat(),
                                onValueChange = { dragMs = it.toLong() },
                                onValueChangeFinished = {
                                    val target = dragMs ?: return@Slider
                                    log.d("seek: $target")
                                    userAction { player.seekTo(target) }
                                    dragMs = null
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Colors.PreviewControls,
                                    activeTrackColor = Colors.PreviewControls,
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(32.dp),
                            )
                            TimeText(DateUtils.formatDuration(durationMs))
                        }
                    } else {
                        // a live stream, or one whose length FFmpeg can't tell
                        TimeText(stringResource(Res.string.video_live))
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                            // nothing to skip through in a live stream
                            if (canSeek) ControlButton(Res.drawable.icon_rewind) { userAction { player.seekBy(-SKIP_BACK_MS) } }
                            ControlButton(
                                res = if (isPlaying) Res.drawable.icon_pause else Res.drawable.icon_play,
                                size = 36,
                            ) { userAction(action = player::togglePlay) }
                            if (canSeek) ControlButton(Res.drawable.icon_forward) { userAction { player.seekBy(SKIP_FORWARD_MS) } }
                        }
                        // off to the side, where a video's own player has it
                        Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                            IconButton(onClick = { userAction(action = ::toggleVideoMuted) }) {
                                Icon(
                                    imageVector = if (isMuted) MetaIcons.VolumeOff else MetaIcons.VolumeOn,
                                    contentDescription = stringResource(if (isMuted) Res.string.video_unmute else Res.string.video_mute),
                                    tint = Colors.PreviewControls,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            ControlButton(
                                res = if (isFullScreen) Res.drawable.icon_fullscreen_exit else Res.drawable.icon_fullscreen,
                            ) { userAction { toggleFullScreen() } }
                        }
                    }
                }
            }
        }
    }

    if (isFullScreen) {
        DisposableEffect(Unit) {
            PlatformUtils.setKeepScreenOn(true)
            onDispose { PlatformUtils.setKeepScreenOn(false) }
        }
    }
    if (isAppFullScreen) {
        // keeps the dialog behind the size it was
        Box(modifier = modifier.background(Colors.PreviewBackground))
        DisposableEffect(Unit) {
            PlatformUtils.setFullScreen(true)
            onDispose { PlatformUtils.setFullScreen(false) }
        }
        // Escape closes this, not the dialog behind
        Dialog(onDismissRequest = { isAppFullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            surface(Modifier.fillMaxSize())
        }
    } else {
        surface(modifier)
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text = text, fontSize = 12.sp, color = Colors.PreviewControls)
}

@Composable
private fun ControlButton(res: DrawableResource, size: Int = 24, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        AppIcon(res = res, modifier = Modifier.size(size.dp), color = Colors.PreviewControls)
    }
}
