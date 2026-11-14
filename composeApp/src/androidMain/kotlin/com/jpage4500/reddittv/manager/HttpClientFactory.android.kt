package com.jpage4500.reddittv.manager

import com.jpage4500.reddittv.util.LoggerConfig
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

private val log = LoggerConfig.createLogger("HttpClient")

// shared across every client this factory builds so evictIdleConnections() can reach the sockets.
// OkHttp's defaults (5 idle connections, 5 minute keep-alive)
private val connectionPool = ConnectionPool()

// IO dispatcher is required, not just tidy: evictAll() closes each socket, and closing a Conscrypt
// TLS socket drains its outgoing queue — StrictMode counts that as network on the main thread
actual suspend fun evictIdleConnections() = withContext(Dispatchers.IO) {
    log.v { "evictIdleConnections: dropping idle keep-alive sockets" }
    // never throws — callers do the device fetch right after, and that matters more than the evict
    try {
        connectionPool.evictAll()
    } catch (e: Exception) {
        log.w("evictIdleConnections: exception: ${e.message}")
    }
}

/**
 * create http client for Android
 */
actual fun createHttpClient(useCookies: Boolean): HttpClient {
    // shared JSON configuration
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        coerceInputValues = true
    }

    return HttpClient(OkHttp) {
        engine {
            // the platform's certificate checks (network_security_config adds user-installed CAs)
            config {
                // force HTTP/1.1 — many IP cameras mishandle HTTP/2 (return 504 or stall on reused connections)
                protocols(listOf(Protocol.HTTP_1_1))
                // OkHttp's Dispatcher defaults to 5 concurrent requests per host, and all hub
                // traffic (device refresh, per-tile hsm/modes polling, commands) shares this one
                // client. on 2026-07-26 a foreground refresh burst stalled on an unreachable hub
                // and parked two user button taps in the ready queue for 2-4s before they hit the
                // wire. NOTE: Ktor installs its own default Dispatcher first, then applies this
                // block, so the override sticks.
                dispatcher(Dispatcher().apply { maxRequestsPerHost = 20 })
                // shared pool (see evictIdleConnections) — without an explicit instance each
                // client builds its own and nothing can reach them to evict on foreground
                connectionPool(connectionPool)
                // keepalive ping for the realtime websocket. MUST be set on the engine — the
                // install(WebSockets){pingInterval} plugin value is ignored by the OkHttp engine
                // (it pings only off OkHttpClient.pingInterval). without this a dead/half-open
                // socket (e.g. NAT idle-timeout while backgrounded) never errors, so the reconnect
                // loop never fires and realtime updates silently stop.
                pingInterval(30, TimeUnit.SECONDS)
            }
        }

        // OkHttp's defaults are 10s connect/read/write. raised so slow commands like
        // Blink GetThumbnail (hub → camera cloud round-trip) don't trip the socket
        // timeout, especially on the cloud fallback path which isn't wrapped by
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000L
            socketTimeoutMillis = 20_000L
            connectTimeoutMillis = 10_000L
        }

        install(ContentNegotiation) {
            json(json)
        }

        // single place that logs all requests/responses (filter by the "HttpLog" tag)
        installHttpLog()

        if (useCookies) {
            // persist session cookies across requests
            install(HttpCookies) {
                storage = appCookieStorage
            }
        }

        // required for webSocket{} — keepalive ping is configured on the engine above, since the
        // OkHttp engine ignores this plugin's pingInterval
        install(WebSockets)
    }
}

