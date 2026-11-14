package com.jpage4500.reddittv.util

/**
 * the app's hidden browser: loads a page with [WebSession]'s cookies and reads it with a script —
 * the fallback for when a site turns away the app's own requests but serves its pages to a browser.
 *
 * Android: an off-screen WebView. desktop: a headless tab of [CdpBrowser]. iOS: not built.
 */
expect object PageScraper {
    val isSupported: Boolean

    /**
     * load [url] and run [script] on it until it answers
     *
     * @param script a JS expression: null while the page isn't ready, "" when there's nothing on it
     * to read, otherwise the string to return
     * @return what [script] answered; null on "", a timeout, or no browser
     */
    suspend fun scrape(url: String, script: String, timeoutMs: Long = 20_000): String?
}

// how often a loading page is asked whether it's ready
internal const val SCRAPE_POLL_MS = 300L
