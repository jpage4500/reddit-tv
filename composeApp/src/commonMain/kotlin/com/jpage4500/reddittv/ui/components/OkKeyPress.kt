package com.jpage4500.reddittv.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import com.jpage4500.reddittv.util.DateUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// how long OK has to be held to count as a long press
private const val LONG_PRESS_MS = 500L
// a KeyDown within this of the last one is the held key repeating (the first repeat comes ~500ms in)
private const val REPEAT_GAP_MS = 1000L
private val OK_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * the remote's OK: [onPress] when it's let go, or [onLongPress] once it's been held
 * [LONG_PRESS_MS] — then letting go does nothing. a release with no press seen here (ie: OK
 * pressed on the feed opened this screen) is ignored
 */
@Composable
fun Modifier.okKeyPress(onPress: () -> Unit, onLongPress: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val press by rememberUpdatedState(onPress)
    val longPress by rememberUpdatedState(onLongPress)
    val state = remember { OkPressState() }
    return onPreviewKeyEvent { event ->
        if (event.key !in OK_KEYS) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                // a held key repeats its KeyDown: only the first starts a press. a KeyDown long
                // after the last one is a new press even with no KeyUp seen — a long press that
                // opened a dialog loses its release to the dialog's window
                val now = DateUtils.nowMs()
                val isRepeat = now - state.lastDownMs < REPEAT_GAP_MS
                state.lastDownMs = now
                if (isRepeat) return@onPreviewKeyEvent true
                state.job?.cancel()
                state.isLong = false
                state.job = scope.launch {
                    delay(LONG_PRESS_MS)
                    state.isLong = true
                    longPress()
                }
            }
            KeyEventType.KeyUp -> {
                state.lastDownMs = 0
                val job = state.job ?: return@onPreviewKeyEvent true
                job.cancel()
                state.job = null
                if (!state.isLong) press()
            }
        }
        true
    }
}

private class OkPressState {
    var job: Job? = null
    var lastDownMs = 0L
    var isLong = false
}
