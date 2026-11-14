package com.jpage4500.reddittv.manager

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlin.concurrent.Volatile

/**
 * create a platform-specific http client.
 * on JVM platforms, this will disable SSL certificate validation for self-signed certificates.
 * @param useCookies install [HttpCookies] for a shared session cookie. Disable for
 *   external traffic (CDNs, video sites, etc.) so a malformed Set-Cookie header
 *   from a third party can't crash the response pipeline.
 */
expect fun createHttpClient(useCookies: Boolean = true): HttpClient

/**
 * drop every idle keep-alive connection so the next request opens a fresh socket.
 *
 * called when the app foregrounds. while backgrounded (Android Doze, laptop sleep) the Wi-Fi link
 * drops and re-associates without the hub ever getting to send a FIN, so the pooled socket is
 * half-open: it still looks healthy to OkHttp's pre-reuse check, the request gets written to it,
 * and nothing comes back until TCP exhausts its retransmit backoff ~14s later. only then does
 * retryOnConnectionFailure open a new connection and finish in milliseconds. evicting first turns
 * that 14s first fetch into a normal one.
 *
 * only closes *idle* connections — anything with a call in flight is left alone.
 *
 * suspend + off the main thread by contract: closing a TLS socket drains its outgoing queue, which
 * is a real network write, and Android's StrictMode turns that into a NetworkOnMainThreadException.
 * never throws — callers fetch devices right after, and that matters more than the eviction.
 */
expect suspend fun evictIdleConnections()

/**
 * shared cookie storage for session cookies. Wrapping AcceptAllCookiesStorage
 * lets us reset it on logout (Ktor's storage has no public clear).
 */
val appCookieStorage = ClearableCookiesStorage()

class ClearableCookiesStorage : CookiesStorage {
    @Volatile
    private var inner: CookiesStorage = AcceptAllCookiesStorage()

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = inner.addCookie(requestUrl, cookie)
    override suspend fun get(requestUrl: Url): List<Cookie> = inner.get(requestUrl)
    override fun close() = inner.close()

    /** drop all stored cookies (called on logout) */
    fun clearAll() {
        inner = AcceptAllCookiesStorage()
    }
}
