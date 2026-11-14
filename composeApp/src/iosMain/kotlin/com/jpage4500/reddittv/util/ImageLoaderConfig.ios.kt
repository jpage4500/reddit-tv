package com.jpage4500.reddittv.util

import coil3.ComponentRegistry
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.jpage4500.reddittv.manager.createHttpClient
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import kotlin.coroutines.CoroutineContext

// bounded dispatcher caps concurrent image fetch/decode so they can't all pile onto Coil's
// DiskLruCache lock. uses Default (not IO) on iOS: the network runs on Ktor's Darwin queue and
// decode is Skia/CPU, so a Default-derived view is adequate and avoids the Native IO import clash
actual val imageFetchDispatcher: CoroutineContext = Dispatchers.Default.limitedParallelism(4)

// Library/Caches, not NSTemporaryDirectory() where Coil defaults: iOS purges tmp/ between launches
// and on storage pressure, and Caches survives both while still being reclaimable
@OptIn(ExperimentalForeignApi::class)
actual fun createImageDiskCache(context: PlatformContext): DiskCache? {
    val cachesDir = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
        NSUserDomainMask,
        true
    ).firstOrNull() as? String ?: return null

    return DiskCache.Builder()
        .directory("$cachesDir/$IMAGE_CACHE_DIR".toPath())
        .maxSizePercent(IMAGE_CACHE_PERCENT)
        .minimumMaxSizeBytes(IMAGE_CACHE_MIN_BYTES)
        .maximumMaxSizeBytes(IMAGE_CACHE_MAX_BYTES)
        .build()
}

// a flat ceiling rather than a percentage: Coil's non-android totalAvailableMemoryBytes is a
// hardcoded 512MB, so a percentage here is a fixed number wearing a percent sign
actual fun createImageMemoryCache(context: PlatformContext): MemoryCache? =
    MemoryCache.Builder()
        .maxSizeBytes(IMAGE_MEMORY_MAX_BYTES)
        .build()

/**
 * iOS implementation - nothing platform-specific (SVG is registered in commonMain)
 */
actual fun ComponentRegistry.Builder.addPlatformDecoders() {
}

// iOS keeps the Ktor fetcher (Darwin engine — unaffected by the OkHttp blocking-close-on-cancel issue)
@OptIn(ExperimentalCoilApi::class)
actual fun ComponentRegistry.Builder.addNetworkFetcher() {
    add(KtorNetworkFetcherFactory(createHttpClient(useCookies = false)))
}

