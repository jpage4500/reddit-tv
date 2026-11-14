package com.jpage4500.reddittv.util

/**
 * desktop-specific console log writer with thread ID support
 */
class DesktopAppLogWriter : AppLogWriter() {

    override fun getThreadId(): String {
        val thread = Thread.currentThread()

        // check if this is the main UI thread
        return if (thread.name.equals("AWT-EventQueue-0", ignoreCase = true)) {
            "UI"
        } else {
            // use last 2 characters of thread name hash as hex
            val hash = thread.name.hashCode()
            "%02X".format(hash and 0xFF)
        }
    }
}

