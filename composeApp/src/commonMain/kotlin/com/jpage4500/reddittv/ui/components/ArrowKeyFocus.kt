package com.jpage4500.reddittv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager

/**
 * a keyboard's arrows move focus, as a remote's do — desktop only handles Tab itself. it sees a key
 * after whatever is focused inside, so a text field, a gallery or a slider keeps its own arrows.
 * a dialog is its own window on desktop, so it needs this too
 */
@Composable
fun Modifier.arrowKeyFocus(): Modifier {
    val focusManager = LocalFocusManager.current
    return onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val direction = when (event.key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionLeft -> FocusDirection.Left
            Key.DirectionRight -> FocusDirection.Right
            else -> return@onKeyEvent false
        }
        focusManager.moveFocus(direction)
    }
}
