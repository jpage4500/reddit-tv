package com.jpage4500.reddittv.util

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.coroutines.resume

private val log = LoggerConfig.createLogger("PageScraper")

actual object PageScraper {
    actual val isSupported: Boolean = true

    @SuppressLint("SetJavaScriptEnabled")
    actual suspend fun scrape(url: String, script: String, timeoutMs: Long): String? = withContext(Dispatchers.Main) {
        val webView = try {
            WebView(AndroidContextProvider.context)
        } catch (e: Exception) {
            // no WebView provider
            log.e("scrape: ${e.message}")
            return@withContext null
        }
        try {
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            // keep redirects in here, not in a browser app
            webView.webViewClient = WebViewClient()
            webView.loadUrl(url)
            val result = withTimeoutOrNull(timeoutMs) {
                var answer: String? = null
                while (answer == null) {
                    delay(SCRAPE_POLL_MS)
                    answer = webView.evaluate(script)
                }
                answer
            }
            log.d("scrape: $url: ${if (result == null) "timed out" else "${result.length} chars"}")
            result?.takeIf { it.isNotEmpty() }
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    /** [script]'s answer if it's a string; null for anything else */
    private suspend fun WebView.evaluate(script: String): String? = suspendCancellableCoroutine { cont ->
        evaluateJavascript(script) { raw ->
            // the result arrives JSON-encoded: "null", or a quoted string
            val value = runCatching { Json.parseToJsonElement(raw ?: "null") as? JsonPrimitive }.getOrNull()
            if (cont.isActive) cont.resume(value?.takeIf { it.isString }?.content)
        }
    }
}
