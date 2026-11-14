package com.jpage4500.reddittv.util

import androidx.compose.runtime.*

private val log = LoggerConfig.createLogger("ComposeDebug")

/**
 * logs every time the composable recomposes
 * usage: LogCompositions("MyComponent")
 */
@Composable
fun LogCompositions(tag: String, vararg keys: Any?) {
    val ref = rememberUpdatedState(keys)
    DisposableEffect(ref.value) {
        log.d { "$tag: RECOMPOSE - keys=${keys.joinToString()}" }
        onDispose {
            log.d { "$tag: DISPOSE" }
        }
    }
}

/**
 * tracks and logs recomposition count
 * usage: val recomposeCount = rememberRecomposeCount("MyComponent")
 */
@Composable
fun rememberRecomposeCount(tag: String): Int {
    val count = remember { mutableStateOf(0) }
    SideEffect {
        count.value++
        log.d { "$tag: recompose count = ${count.value}" }
    }
    return count.value
}

/**
 * logs when state changes
 * usage: val state by rememberLoggedState("MyState", initialValue)
 */
@Composable
fun <T> rememberLoggedState(tag: String, initialValue: T): MutableState<T> {
    val state = remember { mutableStateOf(initialValue) }
    LaunchedEffect(state.value) {
        log.d { "$tag: state changed to ${state.value}" }
    }
    return state
}

/**
 * logs when a composable is disposed
 * usage: LogDispose("MyComponent")
 */
@Composable
fun LogDispose(tag: String) {
    DisposableEffect(Unit) {
        log.d { "$tag: COMPOSED" }
        onDispose {
            log.d { "$tag: DISPOSED" }
        }
    }
}

/**
 * logs when derived state recomputes
 * usage: val derivedValue = remember { derivedStateOf { computation } }.also { LogDerivedState("MyDerived", it.value) }
 */
@Composable
fun <T> LogDerivedState(tag: String, value: T) {
    SideEffect {
        log.d { "$tag: derived state = $value" }
    }
}

/**
 * logs layout measurements
 * can be used as a Modifier extension
 */
fun logLayoutMeasurement(tag: String, width: Int, height: Int) {
    log.d { "$tag: LAYOUT MEASURE - width=$width, height=$height" }
}

/**
 * logs when a key changes in remember/key blocks
 */
@Composable
fun LogKeyChange(tag: String, vararg keys: Any?) {
    remember(*keys) {
        log.d { "$tag: KEY CHANGED - keys=${keys.joinToString()}" }
        Unit
    }
}

