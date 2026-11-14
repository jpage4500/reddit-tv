package com.jpage4500.reddittv.ui.components

import androidx.compose.runtime.*

/**
 * desktop's back button is Escape: the window hands it to [DesktopBack.onEscape], which runs the
 * newest [BackPress] still composed
 */
@Composable
actual fun BackPress(onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    DisposableEffect(Unit) {
        val handler = { current() }
        DesktopBack.handlers.add(handler)
        onDispose { DesktopBack.handlers.remove(handler) }
    }
}

object DesktopBack {
    internal val handlers = mutableListOf<() -> Unit>()

    /** Escape was pressed: true if a [BackPress] took it */
    fun onEscape(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handler()
        return true
    }
}
