package com.jpage4500.reddittv.util

import android.os.Process
import android.util.Log
import co.touchlab.kermit.Severity

/**
 * Android-specific log writer
 */
class AndroidLogWriter : AppLogWriter() {

    override fun getThreadId(): String {
        val thread = Thread.currentThread()
        // check if this is the main UI thread (Android main thread)
        return if (thread.name.contains("main", ignoreCase = true)) {
            "UI"
        } else {
            // use last 2 digits of thread ID as hex
            // NOTE: use Android-specific thread ID (Linux TID)
            val id = Process.myTid()
            "%02X".format(id % 256)
        }
    }

    override fun logToConsole(timeStr: String, severity: Severity, threadId: String, tag: String, message: String) {
        // convert Severity to Int
        val level = when (severity) {
            Severity.Verbose -> Log.VERBOSE
            Severity.Debug -> Log.DEBUG
            Severity.Info -> Log.INFO
            Severity.Warn -> Log.WARN
            Severity.Error -> Log.ERROR
            Severity.Assert -> Log.ASSERT
        }

        // Android's Log has a ~4000 char per-line limit; split longer messages
        if (message.length <= MAX_LOG_LENGTH) {
            Log.println(level, tag, message)
            return
        }
        var index = 0
        val total = message.length
        while (index < total) {
            val end = minOf(index + MAX_LOG_LENGTH, total)
            // prefer to break on a space within the chunk
            val breakAt = if (end < total) {
                val sp = message.lastIndexOf(' ', end - 1)
                if (sp > index) sp + 1 else end
            } else end
            Log.println(level, tag, message.substring(index, breakAt))
            index = breakAt
        }
    }

    companion object {
        private const val MAX_LOG_LENGTH = 4000
    }
}

