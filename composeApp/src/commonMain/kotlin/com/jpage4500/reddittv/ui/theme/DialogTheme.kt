package com.jpage4500.reddittv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * the one look every dialog, popup menu and full-screen panel has: [DialogColorScheme]'s white body
 * under the light title bar, with the brand blue for the title, icons, switches and focus. wrap
 * what's inside the window (a Dialog's content, a DropdownMenu) in it
 */
@Composable
fun DialogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DialogColorScheme, content = content)
}
