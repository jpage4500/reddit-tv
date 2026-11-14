package com.jpage4500.reddittv.util

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

// Compose Dialog carrier with window flags tweaked so the toast floats above any
// other Dialog without blocking input or dimming the underlying UI. FLAG_NOT_TOUCH_MODAL
// passes touches outside the window bounds through to the underlying UI — so we also
// shrink the window to wrap the toast content (WRAP_CONTENT + bottom-center gravity),
// otherwise Compose's `usePlatformDefaultWidth = false` forces width=MATCH_PARENT and
// there's no "outside" for the flag to route around.
@Composable
actual fun ToastOverlayContainer(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.apply {
                setDimAmount(0f)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                )
                setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                setLayout(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                )
            }
            onDispose { }
        }
        Box(modifier = Modifier.padding(bottom = 40.dp)) {
            content()
        }
    }
}
