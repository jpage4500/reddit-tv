package com.jpage4500.reddittv.util

import co.touchlab.kermit.Severity

/**
 * IOS implementation of file log writer
 */
class IOSLogWriter : AppLogWriter() {
    override fun getThreadId(): String {
        // get current thread id and return as 2-digit hex, or "UI" for main thread
        val isMainThread = platform.Foundation.NSThread.isMainThread
        if (isMainThread) {
            return "UI"
        }
        val threadPtr = platform.Foundation.NSThread.currentThread
        val id = threadPtr.hash.toUInt() % 256u // use hash as a unique identifier, limit to 2 hex digits
        return id.toString(16).padStart(2, '0').uppercase()
    }

    override fun logToConsole(timeStr: String, severity: Severity, threadId: String, tag: String, message: String) {
        val logMessage = "[${getSeverityDesc(severity)}] $threadId $tag: $message"
        //NSLog(logMessage)
        println(logMessage)
    }
}