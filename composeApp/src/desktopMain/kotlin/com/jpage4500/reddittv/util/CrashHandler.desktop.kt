package com.jpage4500.reddittv.util

import com.jpage4500.reddittv.manager.LogManager
import kotlin.system.exitProcess

private val log = LoggerConfig.createLogger("CrashHandler")

actual fun installCrashHandler() {
    val prev = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        if (isRecoverable(thread, throwable)) {
            // non-fatal toolkit-thread failure (e.g. JavaFX WebView's broken WebSocket
            // native binding on Linux) — let the thread unwind but keep the app alive
            // instead of recording a crash and exiting into a restart loop. logged only,
            // so it still surfaces in uploaded logs without the crash-report flow.
            log.w("installCrashHandler: recovered from non-fatal ${throwable::class.simpleName} on '${thread.name}': ${throwable.message}")
            return@setDefaultUncaughtExceptionHandler
        }

        // genuinely fatal — record synchronously (process is about to die), then hand off
        // to the prior handler, else print + exit so the user notices the crash
        LogManager.recordCrash(throwable, thread.name)
        if (prev != null) {
            prev.uncaughtException(thread, throwable)
        } else {
            throwable.printStackTrace()
            exitProcess(1)
        }
    }
}

/**
 * true for uncaught errors the app can survive without tearing itself down.
 *
 * JavaFX toolkit-thread exceptions are thread-local — killing the "JavaFX Application Thread"
 * does not require killing the JVM, and our WebView usage (offscreen tile snapshots + the
 * fullscreen embed) is non-essential. the concrete case this guards against: on Linux,
 * libjfxwebkit.so ships broken WebSocket support, so any page that opens a WebSocket (e.g. a
 * TradingView widget tile) throws UnsatisfiedLinkError on
 * com.sun.webkit.network.SocketStreamHandle.twkDidOpen. previously the default handler
 * recorded a crash and exited, and the dashboard re-rendered the same tile on relaunch —
 * an endless crash loop.
 */
private fun isRecoverable(thread: Thread, throwable: Throwable): Boolean {
    // JavaFX toolkit thread — its failures are non-fatal to the rest of the app
    if (thread.name == "JavaFX Application Thread") return true

    // the same native-binding failure can surface on other threads — match it directly
    var t: Throwable? = throwable
    while (t != null) {
        if (t is UnsatisfiedLinkError && t.message?.contains("com.sun.webkit") == true) return true
        t = t.cause
    }
    return false
}
