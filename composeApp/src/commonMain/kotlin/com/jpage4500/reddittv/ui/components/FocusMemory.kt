package com.jpage4500.reddittv.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * which of a dialog's rows the remote was last on. a dialog's window is torn down while another
 * dialog is open over it and built again afterwards, with nothing focused: this is how it gets
 * back onto the row that dialog was opened from, rather than its first one.
 *
 * BaseDialog owns one per dialog and provides it as [LocalFocusMemory]; a row joins in with
 * [rememberFocus]
 */
class FocusMemory {
    var key: Any? = null
    var requester: FocusRequester? = null

    /** focus the remembered row. false when there isn't one (a dialog opening for the first time) */
    fun restore(): Boolean = requester?.requestFocus() ?: false
}

val LocalFocusMemory = staticCompositionLocalOf<FocusMemory?> { null }

/**
 * has the enclosing dialog remember this row once it's focused, by [key] — unique among the
 * dialog's rows, and the same each time it's composed. goes ahead of the clickable in the chain
 */
fun Modifier.rememberFocus(key: Any): Modifier = composed {
    val memory = LocalFocusMemory.current ?: return@composed this
    val requester = remember { FocusRequester() }
    // composed again after the dialog over this one closed: the old requester went with the window
    DisposableEffect(memory, key) {
        if (memory.key == key) memory.requester = requester
        onDispose { if (memory.requester === requester) memory.requester = null }
    }
    focusRequester(requester).onFocusChanged {
        if (it.isFocused) {
            memory.key = key
            memory.requester = requester
        }
    }
}
