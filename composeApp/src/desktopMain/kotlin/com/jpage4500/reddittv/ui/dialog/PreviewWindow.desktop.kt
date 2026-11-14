package com.jpage4500.reddittv.ui.dialog

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.WindowState

/**
 * a window that hosts a video player on its own: its full screen is this window's, not the
 * app's. null anywhere else (the player then uses the main window's state)
 */
val LocalPreviewWindowState = staticCompositionLocalOf<WindowState?> { null }
