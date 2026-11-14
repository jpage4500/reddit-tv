package com.jpage4500.reddittv.util

import kotlinx.coroutines.*
import kotlinx.serialization.json.*

private val log = LoggerConfig.createLogger("PageScraper")

actual object PageScraper {
    actual val isSupported: Boolean
        get() = CdpBrowser.executable != null

    actual suspend fun scrape(url: String, script: String, timeoutMs: Long): String? {
        if (!isSupported) return null
        val tab = try {
            CdpBrowser.connect().newTab()
        } catch (e: Exception) {
            log.e("scrape: ${e.message}")
            return null
        }
        try {
            tab.send("Page.navigate", buildJsonObject { put("url", url) })
            val result = withTimeoutOrNull(timeoutMs) {
                var answer: String? = null
                while (answer == null) {
                    delay(SCRAPE_POLL_MS)
                    answer = tab.evaluate(script)
                }
                answer
            }
            log.d("scrape: $url: ${if (result == null) "timed out" else "${result.length} chars"}")
            return result?.takeIf { it.isNotEmpty() }
        } catch (e: CdpException) {
            log.e("scrape: ${e.message}")
            return null
        } finally {
            withContext(NonCancellable) { tab.close() }
        }
    }
}
