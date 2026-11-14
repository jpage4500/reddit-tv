package com.jpage4500.reddittv.manager

import com.jpage4500.reddittv.util.DesktopUtils
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
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Dispatcher
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private val log = LoggerConfig.createLogger("HttpClient")

// shared across every client this factory builds so evictIdleConnections() can reach the sockets.
// OkHttp's defaults (5 idle connections, 5 minute keep-alive)
private val connectionPool = ConnectionPool()

// off the UI thread to match the contract on the expect — evictAll() closes each socket, and
// closing a TLS socket writes a close_notify
actual suspend fun evictIdleConnections() = withContext(Dispatchers.IO) {
    log.d("evictIdleConnections: dropping idle keep-alive sockets")
    // never throws — callers do the device fetch right after, and that matters more than the evict
    try {
        connectionPool.evictAll()
    } catch (e: Exception) {
        log.w("evictIdleConnections: exception: ${e.message}")
    }
}

/**
 * create http client for desktop/JVM
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
        install(UserAgent) {
            agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }

        engine {
            // accept the platform's full set of TLS versions and cipher suites — many IP cameras
            // (Reolink, etc.) negotiate older/legacy ciphers that OkHttp's default MODERN_TLS spec
            // rejects, surfacing as "Received fatal alert: handshake_failure"
            val compatibleTls = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
                .allEnabledTlsVersions()
                .allEnabledCipherSuites()
                .build()

            config {
                // 1. Define the cache directory
                val cacheDir = DesktopUtils.getHomeDir("http_cache")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }

                // 2. Set a strict size limit (e.g., 50 Megabytes)
                val cacheSize = 50L * 1024L * 1024L

                // 3. Apply the cache to OkHttp
                cache(Cache(cacheDir, cacheSize))

                sslSocketFactory(legacyTolerantSocketFactory, defaultTrustManager)
                connectionSpecs(listOf(compatibleTls, ConnectionSpec.CLEARTEXT))
                followRedirects(true)
                // OkHttp's Dispatcher defaults to 5 concurrent requests per host, and all hub
                // traffic (device refresh, per-tile hsm/modes polling, commands) shares this one
                // client — a stalled refresh burst can otherwise park a user's tap in the ready
                // queue for seconds. NOTE: Ktor installs its own default Dispatcher first, then
                // applies this block, so the override sticks.
                dispatcher(Dispatcher().apply { maxRequestsPerHost = 20 })
                // shared pool (see evictIdleConnections) — without an explicit instance each
                // client builds its own and nothing can reach them to evict on foreground
                connectionPool(connectionPool)
                // keepalive ping for the realtime websocket. MUST be set on the engine — the
                // install(WebSockets){pingInterval} plugin value is ignored by the OkHttp engine
                // (it pings only off OkHttpClient.pingInterval). without this a dead/half-open
                // socket (e.g. NAT idle-timeout while the laptop lid is closed) never errors, so
                // the reconnect loop never fires and realtime updates silently stop.
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

// the JVM's own trust store: certificates are checked as normal
internal val defaultTrustManager: X509TrustManager by lazy {
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(null as KeyStore?) }
        .trustManagers.filterIsInstance<X509TrustManager>().first()
}

internal val legacyTolerantSocketFactory: SSLSocketFactory by lazy {
    val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(defaultTrustManager), null) }
    LegacyTolerantSslSocketFactory(sslContext.socketFactory)
}

/**
 * forces every created socket to advertise all supported TLS versions and cipher suites — the
 * JDK otherwise narrows these to the modern subset, which some IP cameras reject with
 * "handshake_failure".
 */
internal class LegacyTolerantSslSocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(): Socket = configure(delegate.createSocket() as SSLSocket)
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        configure(delegate.createSocket(s, host, port, autoClose) as SSLSocket)
    override fun createSocket(host: String, port: Int): Socket =
        configure(delegate.createSocket(host, port) as SSLSocket)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        configure(delegate.createSocket(host, port, localHost, localPort) as SSLSocket)
    override fun createSocket(host: InetAddress, port: Int): Socket =
        configure(delegate.createSocket(host, port) as SSLSocket)
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        configure(delegate.createSocket(address, port, localAddress, localPort) as SSLSocket)

    private fun configure(socket: SSLSocket): SSLSocket {
        // exclude SSLv2Hello — it's a legacy ClientHello compatibility shim, not a real
        // protocol, and OkHttp throws IllegalArgumentException when it sees it
        socket.enabledProtocols = socket.supportedProtocols.filter { it != "SSLv2Hello" }.toTypedArray()
        socket.enabledCipherSuites = socket.supportedCipherSuites
        return socket
    }
}

