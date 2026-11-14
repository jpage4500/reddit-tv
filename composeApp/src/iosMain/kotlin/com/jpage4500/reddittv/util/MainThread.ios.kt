package com.jpage4500.reddittv.util

import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * run [block] on the main thread — inline when we're already on it, otherwise on the main queue.
 *
 * UIKit is main-thread only and iOS enforces it: touched from anywhere else it doesn't misbehave
 * quietly, it aborts the process with `threading violation: expected the main thread`. almost
 * nothing in this app runs on the main thread by default — every manager owns a
 * `Dispatchers.Default` scope, so a pref watcher, a schedule tick or a weather refresh all arrive
 * here on a worker — which makes the platform layer the right place to hop rather than each caller.
 *
 * the inline fast path is deliberate: a call already on the main thread keeps running in order with
 * whatever the UI did next, rather than being deferred to the following run-loop turn.
 */
internal fun onMainThread(block: () -> Unit) {
    if (NSThread.isMainThread()) block()
    else dispatch_async(dispatch_get_main_queue()) { block() }
}
