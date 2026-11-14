package com.jpage4500.reddittv.util

// no hidden browser on iOS yet (it has no sign-in browser either)
actual object PageScraper {
    actual val isSupported: Boolean = false

    actual suspend fun scrape(url: String, script: String, timeoutMs: Long): String? = null
}
