package com.jpage4500.reddittv.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import com.jpage4500.reddittv.util.PlatformUtils

/**
 * keeps a TV's keyboard closed until it's asked for. focusing a text field normally opens it, and
 * Fire TV's is full screen and takes every D-pad press — back closes it, but the next press down
 * lands on the field and opens it again, so nothing below the field (ie: Send) can be reached.
 *
 * on a TV the field stays read-only (no keyboard) while the remote passes over it, until OK is
 * pressed on it; it goes back once focus leaves it or the keyboard's Done is pressed. elsewhere it
 * changes nothing. pass [readOnly] and [modifier] to the text field
 */
@Stable
class TvTextInputState internal constructor(private val isTv: Boolean) {
    var isEditing by mutableStateOf(false)
        private set

    val readOnly: Boolean
        get() = isTv && !isEditing

    val modifier: Modifier = if (!isTv) Modifier else Modifier
        .onFocusChanged { if (!it.isFocused) isEditing = false }
        .onPreviewKeyEvent { event ->
            if (isEditing || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    // turning off read-only while focused starts the input session, opening the keyboard
                    isEditing = true
                    true
                }

                else -> false
            }
        }

    /**
     * the keyboard's Done: close it and go back to moving around with the remote. returns false
     * when not on a TV, so the caller can fall back to the default action
     */
    fun onDone(): Boolean {
        if (!isTv) return false
        isEditing = false
        return true
    }
}

@Composable
fun rememberTvTextInputState(): TvTextInputState = remember { TvTextInputState(PlatformUtils.isTv()) }
