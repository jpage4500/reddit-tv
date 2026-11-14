package com.jpage4500.reddittv.ui.dialog.common

import com.jpage4500.reddittv.ui.theme.DialogTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jpage4500.reddittv.ui.components.DialogTitle
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.PlatformType
import com.jpage4500.reddittv.util.Utils
import org.jetbrains.compose.resources.DrawableResource

private val log = LoggerConfig.createLogger("DeviceFullScreenDialog")

/**
 * dialog showing a large device view with title
 * used for quick device interaction/viewing
 */
@Composable
fun FullScreenDialog(
    title: String,
    icon: DrawableResource? = null,
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val properties = DialogProperties(true, true, false)
    Dialog(onDismissRequest = onDismiss, properties = properties) {
        FullScreenContent(
            title = title,
            icon = icon,
            onDismiss = onDismiss,
            content = content,
        )
    }
}

/**
 * same as [FullScreenDialog], except on iOS where it renders inline in the calling scene instead of in a
 * platform Dialog.
 *
 * a Dialog on iOS creates a second compose scene layer whose geometry is unreliable across rotation —
 * unbounded constraints, dropped safe-area insets, and interop frames that diverge from their layout
 * slot, all documented in "docs/iOS Video Player Lifecycle.md". The main scene has
 * none of those problems, and a Dialog buys nothing on iOS anyway: there's no hardware back button and a
 * full-screen dialog has no outside to tap, so both DialogProperties flags are inert.
 *
 * the [BoxScope] receiver is the contract: on iOS this lays out in place, so it covers exactly its
 * parent's area and that parent must be a box filling the window. callers that can't guarantee that
 * should use [FullScreenDialog] instead.
 */
@Composable
fun BoxScope.FullScreenDeviceView(
    title: String,
    icon: DrawableResource? = null,
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    if (!Utils.isPlatform(PlatformType.IOS)) {
        FullScreenDialog(title = title, icon = icon, onDismiss = onDismiss, content = content)
        return
    }
    Box(
        modifier = Modifier
            // matchParentSize so the overlay covers the parent without influencing its size
            .matchParentSize()
            // the Dialog got platform insets for free; inline has to ask. self-adjusting: with the
            // full-screen pref on, iOS negates the insets and hides the status bar, so zero is right
            .safeDrawingPadding()
            // a pointer handler makes this node hit-testable so taps stop here instead of falling
            // through to whatever is underneath. it doesn't consume, so the title bar and any controls
            // inside still receive their events
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
    ) {
        FullScreenContent(title = title, icon = icon, onDismiss = onDismiss, content = content)
    }
}

/**
 * the card body shared by [FullScreenDialog] and [FullScreenDeviceView].
 */
@Composable
fun FullScreenContent(
    title: String,
    icon: DrawableResource? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable BoxScope.() -> Unit
) = DialogTheme {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            DialogTitle(title = title, icon = icon, onDismiss = onDismiss)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                content()
            }
        }
    }
}
