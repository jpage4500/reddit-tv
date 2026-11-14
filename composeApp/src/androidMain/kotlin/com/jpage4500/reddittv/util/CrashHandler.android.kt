package com.jpage4500.reddittv.util

import com.jpage4500.reddittv.manager.LogManager

private val log = LoggerConfig.createLogger("CrashHandler")

actual fun installCrashHandler() {
    val prev = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        if (isRecoverable(throwable)) {
            // non-fatal failure from in-process system code — let the thread unwind but keep
            // the app alive instead of recording a crash and killing the process. logged only,
            // so it still surfaces in uploaded logs without the crash-report flow.
            log.w("installCrashHandler: recovered from non-fatal ${throwable::class.simpleName} on '${thread.name}': ${throwable.message}")
            return@setDefaultUncaughtExceptionHandler
        }

        LogManager.recordCrash(throwable, thread.name)
        // delegate to the platform's previous handler (Android default kills the process and shows ANR)
        prev?.uncaughtException(thread, throwable)
    }
}

/**
 * true for uncaught errors the app can survive without tearing itself down.
 *
 * the concrete case this guards against: something running inside our process — Android System
 * WebView (chromium's browser side is in-process) or a Play Services dynamite module — broadcasts
 * android.intent.action.CLOSE_SYSTEM_DIALOGS to collapse the notification shade. since Android 12
 * that broadcast requires the signature|privileged BROADCAST_CLOSE_SYSTEM_DIALOGS permission, so
 * ActivityManager throws SecurityException back into our process and the default handler kills the
 * app. nothing in this app sends it and we can't be granted the permission; the broadcast is purely
 * cosmetic, so swallowing it is safe.
 */
private fun isRecoverable(throwable: Throwable): Boolean {
    var t: Throwable? = throwable
    while (t != null) {
        if (t is SecurityException && t.message?.contains("CLOSE_SYSTEM_DIALOGS") == true) return true
        t = t.cause
    }
    return false
}
