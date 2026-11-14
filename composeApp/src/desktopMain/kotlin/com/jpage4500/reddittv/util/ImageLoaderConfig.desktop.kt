package com.jpage4500.reddittv.util

import coil3.ComponentRegistry
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.jpage4500.reddittv.manager.defaultTrustManager
import com.jpage4500.reddittv.manager.legacyTolerantSocketFactory
import kotlinx.coroutines.Dispatchers
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

// bounded view of the IO pool — caps concurrent image fetch/decode so they can't all pile onto
// Coil's DiskLruCache lock or starve Dispatchers.Default
actual val imageFetchDispatcher: CoroutineContext = Dispatchers.IO.limitedParallelism(4)

// under the app home dir (~/.cast-to-tv) rather than /tmp: macOS sweeps its per-user temp dir
// on a timer and Windows/Linux clear theirs on reboot, which would re-download the whole background
// set every few days
actual fun createImageDiskCache(context: PlatformContext): DiskCache? =
    DiskCache.Builder()
        .directory(File(DesktopUtils.getHomeDir(), IMAGE_CACHE_DIR).toOkioPath())
        .maxSizePercent(IMAGE_CACHE_PERCENT)
        .minimumMaxSizeBytes(IMAGE_CACHE_MIN_BYTES)
        .maximumMaxSizeBytes(IMAGE_CACHE_MAX_BYTES)
        .build()

// a flat ceiling rather than a percentage, for the same reason as iOS: Coil's non-android
// totalAvailableMemoryBytes is a hardcoded 512MB and says nothing about the machine
actual fun createImageMemoryCache(context: PlatformContext): MemoryCache? =
    MemoryCache.Builder()
        .maxSizeBytes(IMAGE_MEMORY_MAX_BYTES)
        .build()

/**
 * desktop implementation - no extra platform decoders (SVG is registered in commonMain;
 * animated GIF is not supported on JVM)
 */
actual fun ComponentRegistry.Builder.addPlatformDecoders() {
}

/**
 * desktop uses Coil's native OkHttp fetcher rather than the Ktor fetcher. the Ktor/OkHttp engine
 * drains a chunked response body with a blocking socket read when its request job is cancelled (Coil
 * cancels image jobs during recomposition); the OkHttp fetcher reads/closes bodies off-thread instead.
 */
actual fun ComponentRegistry.Builder.addNetworkFetcher() {
    add(OkHttpNetworkFetcherFactory(callFactory = { imageOkHttpClient }))
}

// mirrors HttpClientFactory.desktop: the JVM's certificate checks, and COMPATIBLE_TLS with all
// enabled versions/ciphers (avoids handshake_failure on legacy servers). no cookies needed for images.
private val imageOkHttpClient: OkHttpClient by lazy {
    val compatibleTls = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
        .allEnabledTlsVersions()
        .allEnabledCipherSuites()
        .build()

    OkHttpClient.Builder()
        .sslSocketFactory(legacyTolerantSocketFactory, defaultTrustManager)
        .addInterceptor { chain ->
            // default UA is "okhttp/x.y", which Wikimedia rejects with 403; present a browser UA
            val req = chain.request()
            val out = if (req.header("User-Agent") != null) req
            else req.newBuilder().header("User-Agent", IMAGE_USER_AGENT).build()
            chain.proceed(out)
        }
        .connectionSpecs(listOf(compatibleTls, ConnectionSpec.CLEARTEXT))
        .followRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
}
