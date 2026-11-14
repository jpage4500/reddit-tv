package com.jpage4500.reddittv.util

import coil3.ComponentRegistry
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

// bounded view of the IO pool — caps concurrent image fetch/decode so they can't all pile onto
// Coil's DiskLruCache lock or starve Dispatchers.Default
actual val imageFetchDispatcher: CoroutineContext = Dispatchers.IO.limitedParallelism(4)

// the app's own cache dir: cleared with app data, reclaimable by the OS under storage pressure, and
// (unlike the system temp dir Coil defaults to) not swept out from under a running app
actual fun createImageDiskCache(context: PlatformContext): DiskCache? =
    DiskCache.Builder()
        .directory(File(AndroidContextProvider.context.cacheDir, IMAGE_CACHE_DIR).toOkioPath())
        .maxSizePercent(IMAGE_CACHE_PERCENT)
        .minimumMaxSizeBytes(IMAGE_CACHE_MIN_BYTES)
        .maximumMaxSizeBytes(IMAGE_CACHE_MAX_BYTES)
        .build()

// a share of the app heap — which is what a decoded bitmap is competing for here, and what an OOM
// runs out of. largeHeap moves this with it: Coil reads largeMemoryClass when the flag is set
actual fun createImageMemoryCache(context: PlatformContext): MemoryCache? =
    MemoryCache.Builder()
        .maxSizePercent(context, IMAGE_MEMORY_PERCENT)
        .build()

/**
 * android implementation - nothing platform-specific (SVG is registered in commonMain)
 */
actual fun ComponentRegistry.Builder.addPlatformDecoders() {
}

/**
 * android uses Coil's native OkHttp fetcher rather than the Ktor fetcher: the Ktor/OkHttp engine
 * drains a chunked response body with a blocking socket read when its request job is cancelled, and
 * Coil cancels image jobs on the main thread during recomposition — crashing with
 * NetworkOnMainThreadException. the OkHttp fetcher reads/closes bodies off the main thread.
 */
actual fun ComponentRegistry.Builder.addNetworkFetcher() {
    add(OkHttpNetworkFetcherFactory(callFactory = { imageOkHttpClient }))
}

// mirrors HttpClientFactory.android: the platform's certificate checks, and HTTP/1.1 forced. no
// cookies needed for images.
private val imageOkHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .addInterceptor { chain ->
            // default UA is "okhttp/x.y", which Wikimedia rejects with 403; present a browser UA
            val req = chain.request()
            val out = if (req.header("User-Agent") != null) req
            else req.newBuilder().header("User-Agent", IMAGE_USER_AGENT).build()
            chain.proceed(out)
        }
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
}

