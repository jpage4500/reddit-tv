package com.jpage4500.reddittv.ui.dialog

import com.jpage4500.reddittv.ui.theme.DialogTheme
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jpage4500.reddittv.ui.components.DialogButtons
import com.jpage4500.reddittv.ui.components.DialogTitle
import com.jpage4500.reddittv.ui.components.FocusMemory
import com.jpage4500.reddittv.ui.components.LocalFocusMemory
import com.jpage4500.reddittv.ui.components.arrowKeyFocus
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.Utils
import com.jpage4500.reddittv.util.PlatformUtils
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val log = LoggerConfig.createLogger("BaseDialog")

/**
 * Basic dialog with title (top), content (center), and optional OK/CANCEL buttons (bottom)
 */
@Composable
fun BaseDialog(
    title: String? = null,
    titleRes: StringResource? = null,
    icon: DrawableResource? = null,
    okText: String? = null,         // optional OK button
    cancelText: String? = null,     // optional CANCEL button
    okEnabled: Boolean = true,          // if OK button is enabled (only applies if okText is not null)
    onConfirm: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,     // CANCEL's own action; null = it just closes the dialog
    dismissOnConfirm: Boolean = true,   // if true, OK click also calls onDismiss
    focusConfirm: Boolean = false,      // TV: OK starts focused, rather than the content or title's X
    waitForOkRelease: Boolean = false,  // TV: opened by a long-press still holding OK — its repeats and release are ignored
    onDismiss: () -> Unit,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    modifier: Modifier = Modifier.padding(16.dp),
    contentPadding: PaddingValues = PaddingValues(10.dp, 10.dp, 10.dp, 10.dp),
    // false = omit the parent verticalScroll. needed when content embeds its own
    // scrollable/gesture-consuming child (e.g. a WebView-based MapView), since the
    // parent scroll otherwise steals drag events on Android.
    scrollable: Boolean = true,
    // true = shrink the dialog width to its measured content instead of using a fixed
    // screen fraction. used for content that sizes itself (e.g. a square device view),
    // which would otherwise leave empty side gaps in a wide/landscape dialog.
    fitContentWidth: Boolean = false,
    // fraction of the screen width the dialog occupies on non-compact (tablet/desktop) screens.
    // compact screens always fill the width; ignored when fitContentWidth is set.
    widthFraction: Float = 0.5f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val title = title ?: titleRes?.let { stringResource(it) }

    val properties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        usePlatformDefaultWidth = false,
    )

    // both hoisted above the [isTopDialog] check, so a dialog waiting behind an inner one comes
    // back scrolled where the user left it and at the width it already measured
    val scrollState = rememberScrollState()
    // when fitting content width, we measure the content once and snap the dialog to it. null
    // until that first measure, which falls back to maxDialogHeight below (a good guess for square
    // content) so there's no visible wide-then-shrink flash
    var contentWidthPx by remember { mutableStateOf<Int?>(null) }
    // the held OK keeps repeating into this window: left alone, a repeat presses whatever's focused
    // and the release clicks it
    var isWaitingForOk by remember { mutableStateOf(waitForOkRelease) }
    // the row the remote was on, to go back to: the window below is built again, with nothing
    // focused, each time a dialog opened from this one closes
    val focusMemory = remember { FocusMemory() }

    // a dialog opened from another one is a step deeper into one flow, not a second thing on
    // screen: stacked cards, and a scrim per level darkening the one behind it. only the innermost
    // one draws — every level stays composed either way, so nothing it holds is lost.
    // the screensaver effect above deliberately sits above this check, so a level that's currently
    // hidden still closes when the overlay takes over
    if (!isTopDialog()) return

    val isTv = remember { PlatformUtils.isTv() }
    // a remote or a keyboard: something has to have focus for its keys to land anywhere
    val isKeyboard = remember { Utils.isFocusDriven() }

    Dialog(onDismissRequest = onDismiss, properties = properties) {
      DialogTheme {
        // a remote can't point: nothing is focused when a dialog opens, and the D-pad has nowhere to
        // start from. inside the Dialog, so a level coming back on top gets focused again too
        val dialogFocus = remember { FocusRequester() }
        val contentFocus = remember { FocusRequester() }
        val buttonsFocus = remember { FocusRequester() }
        val okFocus = remember { FocusRequester() }
        var hasFocus by remember { mutableStateOf(false) }
        // the window focuses the first thing it finds, which is the title's X
        var titleHasFocus by remember { mutableStateOf(false) }
        if (isTv && focusConfirm && okText != null) {
            // regardless of [hasFocus]: whatever the window focused by itself (the title's X) loses
            LaunchedEffect(Unit) {
                withFrameNanos { }
                try {
                    okFocus.requestFocus()
                } catch (e: Exception) {
                    log.w("focus: ${e.message}")
                }
            }
        }
        if (isKeyboard) {
            // on open, and again if focus is ever lost — with nothing focused, the remote's keys
            // reach nothing in the dialog. on the X counts as nothing on open: the window put it
            // there, not the user (who reaches it by pressing UP, which doesn't re-run this)
            LaunchedEffect(hasFocus) {
                if (hasFocus && !titleHasFocus) return@LaunchedEffect
                // a frame first: the window has to be laid out, and a dialog that focuses its own
                // field (TextInputDialog) has had its turn by then
                withFrameNanos { }
                if (hasFocus && !titleHasFocus) return@LaunchedEffect
                log.v { "focus: nothing focused in ${title ?: "dialog"}, taking it" }
                try {
                    // the row a dialog was opened from, when coming back from it. else content
                    // first, then the buttons: the title's X is focusable, and would win if the
                    // whole dialog were entered. it's the last resort, for a dialog with nothing
                    // else to focus
                    focusMemory.restore() || contentFocus.requestFocus() || buttonsFocus.requestFocus() || dialogFocus.requestFocus()
                } catch (e: Exception) {
                    log.w("focus: ${e.message}")
                }
            }
        }

        // measure inside the Dialog window so nested dialogs see the actual screen size,
        // not the parent dialog's content width. without this, BoxWithConstraints would
        // inherit constraints from the call site — and a nested BaseDialog would
        // misread its parent's narrow content column as a compact screen.
        BoxWithConstraints {
            val isCompactScreen = maxWidth < 600.dp
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            val maxDialogHeight = screenHeight * 0.9f

            val density = LocalDensity.current
            val baseModifier = when {
                fitContentWidth -> {
                    val fitWidth = contentWidthPx?.let { with(density) { it.toDp() } } ?: maxDialogHeight
                    modifier.width(fitWidth.coerceIn(0.dp, screenWidth * 0.9f))
                }
                isCompactScreen -> modifier.fillMaxWidth()
                else -> modifier.width(screenWidth * widthFraction)
            }
            val cardModifier = baseModifier
                .heightIn(max = maxDialogHeight)
            Card(
                modifier = cardModifier,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                            if (!isWaitingForOk || !isOk) return@onPreviewKeyEvent false
                            if (event.type == KeyEventType.KeyUp) isWaitingForOk = false
                            true
                        }
                        // desktop: arrows move between the rows and buttons (a remote's D-pad does already)
                        .then(if (isKeyboard && !isTv) Modifier.arrowKeyFocus() else Modifier)
                        .onFocusChanged { hasFocus = it.hasFocus }
                        .focusRequester(dialogFocus)
                        .focusGroup(),
                ) {
                    if (title != null) {
                        Box(modifier = Modifier.onFocusChanged { titleHasFocus = it.hasFocus }) {
                            DialogTitle(title = title, icon = icon, onDismiss = onDismiss)
                        }
                    }

                    val contentModifier = Modifier
                        // measure the full content wid
                        // th (incl. padding) so the card can shrink
                        // to it. must be outermost: measuring inside .padding() would report the
                        // padding-less width, and re-subtracting padding each pass shrinks to 0.
                        .let { if (fitContentWidth) it.onSizeChanged { size -> contentWidthPx = size.width } else it }
                        .padding(contentPadding)
                        .weight(1f, fill = false)
                        .let { if (scrollable) it.verticalScroll(scrollState) else it }
                        .focusRequester(contentFocus)
                        .focusGroup()

                    CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
                        Column(
                            horizontalAlignment = horizontalAlignment,
                            verticalArrangement = Arrangement.Top,
                            modifier = contentModifier,
                            content = content
                        )
                    }

                    // buttons (optional)
                    if (okText != null || cancelText != null) {
                        DialogButtons(
                            okText = okText,
                            cancelText = cancelText,
                            okEnabled = okEnabled,
                            okFocusRequester = okFocus,
                            onOk = {
                                onConfirm?.invoke()
                                // optionally auto-dismiss; flows that advance to a new sub-state opt out
                                if (dismissOnConfirm) onDismiss()
                            },
                            onCancel = {
                                onCancel?.invoke()
                                onDismiss()
                            },
                            modifier = Modifier
                                .focusRequester(buttonsFocus)
                                .focusGroup()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
      }
    }
}
