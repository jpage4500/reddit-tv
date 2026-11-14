package com.jpage4500.reddittv.manager

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * no-op on iOS: NSURLSession owns its connection cache and has no public "drop idle sockets" call
 * (URLSession.reset() would also wipe cookies + credentials). it also suspends rather than dozes,
 * and tears its own connections down on suspend, so the half-open-socket stall this exists for
 * doesn't reproduce here.
 */
actual suspend fun evictIdleConnections() {}

/**
 * create http client for iOS
 */
actual fun createHttpClient(useCookies: Boolean): HttpClient {
    // shared JSON configuration
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        coerceInputValues = true
    }

    return HttpClient(Darwin) {
        engine {
            // NSURLSession allows 6 concurrent connections per host by default, and all hub traffic
            // (device refresh, per-tile hsm/modes polling, commands) shares this one client — a
            // refresh burst stalled on an unreachable hub can otherwise park a user's tap until one
            // of them times out. matches the OkHttp maxRequestsPerHost override on Android/desktop.
            configureSession {
                HTTPMaximumConnectionsPerHost = 20
            }
        }

        // Darwin/NSURLSession's defaults are much longer than OkHttp's, but pinning
        // explicit values keeps cross-platform behaviour aligned. raised so slow
        // commands like Blink GetThumbnail (hub → camera cloud round-trip) don't trip
        // the socket timeout, especially on the cloud fallback path which isn't
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

        install(WebSockets) {
            // ping the hub periodically so a dead/half-open socket (e.g. after device
            // sleep/wake) surfaces as an error and triggers a reconnect
            pingInterval = 30.seconds
        }
    }
}

