package com.jpage4500.reddittv.util

import android.webkit.CookieManager
import android.webkit.WebSettings
import com.jpage4500.reddittv.data.SignInSite

private val log = LoggerConfig.createLogger("WebSession")

actual object WebSession {
    actual val isSupported: Boolean = true

    actual val userAgent: String? by lazy {
        try {
            WebSettings.getDefaultUserAgent(AndroidContextProvider.context)
        } catch (e: Exception) {
            // no WebView provider
            log.e("userAgent: ${e.message}")
            null
        }
    }

    actual fun cookies(url: String): String? =
        try {
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            log.e("cookies: ${e.message}")
            null
        }

    actual fun isSignedIn(site: SignInSite): Boolean =
        cookies(site.homeUrl).orEmpty().split(';').any { it.trim().startsWith("${site.sessionCookie}=") }

    actual fun signOut(site: SignInSite) {
        val manager = CookieManager.getInstance()
        val domain = site.homeUrl.substringAfter("://").substringBefore('/').removePrefix("www.")
        // CookieManager can't delete by domain: expire each cookie it would send there
        cookies(site.homeUrl).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }.forEach { name ->
            manager.setCookie(site.homeUrl, "$name=; Max-Age=0; Path=/; Domain=.$domain")
            manager.setCookie(site.homeUrl, "$name=; Max-Age=0; Path=/")
        }
        manager.flush()
        log.d("signOut: ${site.label}, signed in now:${isSignedIn(site)}")
    }

    actual fun flush() = CookieManager.getInstance().flush()

    // CookieManager answers straight from its own store
    actual suspend fun refresh() {}
}
