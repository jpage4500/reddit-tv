package com.jpage4500.reddittv.util

import coil3.ComponentRegistry
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

/**
 * configuration for Coil3 image loader
 * handles custom Ktor client for image loading
 */
object ImageLoaderConfig {

    private val log = LoggerConfig.createLogger("ImageLoader")

    // phase 1 observability: a single global hook for every Coil load (all platforms + the android
    // widget). logs real I/O loads (network/disk — memory-cache hits are skipped to stay quiet) and
    // every failure with a best-effort retryable/permanent classification (see classifyImageError).
    private val imageEventListener = object : EventListener() {
        //override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        //    val src = result.dataSource
        //    if (src == DataSource.NETWORK) {
        //        log.v { "onSuccess: $src ${request.data}" }
        //    }
        //}

        override fun onError(request: ImageRequest, result: ErrorResult) {
            val t = result.throwable
            // request short-circuited by the negative cache (see NegativeCacheInterceptor) — the real
            // failure was already logged; keep the skip at verbose so cooldown re-hits don't spam
            if (t is ImageCooldownException) {
                log.v { "onError: cooldown-skip url=${request.data}" }
                return
            }
            log.w("onError: kind=${classifyImageError(t)} type=${t::class.simpleName} url=${request.data} msg=${t.message}")
        }
    }

    /**
     * register Coil's singleton ImageLoader once per process. uses SingletonImageLoader.setSafe,
     * so re-registration is a no-op — safe to call from multiple entry points:
     *  - Android: MainApplication.onCreate (covers widget-launched Compose hosts that never invoke App())
     *  - All platforms: App() composition (covers desktop + iOS)
     */
    @OptIn(ExperimentalCoilApi::class)
    fun registerSingleton() {
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx)
                .components {
                    // network fetcher is added per-platform via expect/actual:
                    // Android/Desktop use Coil's OkHttp fetcher (the Ktor fetcher drains chunked
                    // response bodies with a blocking socket read during job cancellation, which on
                    // Android crashes with NetworkOnMainThreadException). iOS keeps the Ktor fetcher.
                    addNetworkFetcher()
                    add(SvgDecoder.Factory())
                    // GIF decoder is added per-platform via expect/actual
                    addPlatformDecoders()
                    // phase 3: short-circuit URLs that recently failed so a broken source isn't
                    // re-hit on every recomposition (see NegativeCacheInterceptor)
                    add(NegativeCacheInterceptor())
                }
                // run fetch + decode on a dedicated, bounded IO dispatcher (per-platform actual:
                // Dispatchers.IO.limitedParallelism). this caps how many image jobs can pile onto
                // Coil's single DiskLruCache lock during journal init, and keeps image I/O from
                // starving Dispatchers.Default work (device refresh, ICS parsing, viewmodel derivation)
                .fetcherCoroutineContext(imageFetchDispatcher)
                .decoderCoroutineContext(imageFetchDispatcher)
                // Coil's default disk cache lives in the system temp directory, which iOS purges and
                // desktop OSes sweep — fine for icons, wrong for full-screen weather backgrounds that
                // rotate through a fixed set all day. point it at a stable per-app directory instead
                // so a photo is downloaded once and re-shown from disk on every later rotation
                .diskCache { createImageDiskCache(ctx) }
                // ...and an explicit ceiling on the decoded bitmaps held in memory. Coil's own
                // default is a share of the heap on android and a share of a *hardcoded* 512MB
                // everywhere else, which on iOS and desktop is a fixed 77MB whatever the machine is
                .memoryCache { createImageMemoryCache(ctx) }
                .crossfade(true)
                .eventListener(imageEventListener)
                .build()
        }
    }
}

// browser-style User-Agent for image fetches. Coil's OkHttp fetcher (Android/Desktop) otherwise
// sends the default "okhttp/x.y" UA, which Wikimedia — and some other CDNs — reject with HTTP 403.
// iOS's Ktor fetcher already sends an accepted UA, so this is only wired into the OkHttp clients.
internal const val IMAGE_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

// dedicated, bounded dispatcher for Coil fetch + decode (each platform: Dispatchers.IO.limitedParallelism)
expect val imageFetchDispatcher: CoroutineContext

// where each platform keeps its image cache. see IMAGE_CACHE_DIR / the size constants below
expect fun createImageDiskCache(context: PlatformContext): DiskCache?

// how big each platform lets the in-memory bitmap cache get. see the constants below
expect fun createImageMemoryCache(context: PlatformContext): MemoryCache?

// subdirectory each platform's cache root gets. named rather than shared with Coil's default so an
// old default-location cache doesn't get adopted with the wrong size limits
internal const val IMAGE_CACHE_DIR = "image_cache"

// the background set is the largest thing in here: at ~350KB a photo, one mood's 15 candidates is
// ~5MB, and a dashboard cycling day/night through a few conditions lands well inside the floor.
// sized as a share of free space so a full device isn't handed a fixed number it can't honour
internal const val IMAGE_CACHE_PERCENT = 0.02

internal const val IMAGE_CACHE_MIN_BYTES = 48L * 1024 * 1024

internal const val IMAGE_CACHE_MAX_BYTES = 192L * 1024 * 1024

// the memory cache holds *decoded* bitmaps, which is a different order of cost from the disk cache
// above: a full-screen photo is its pixel count times four however small the file was. what this app
// actually draws at once is a couple of those, a map's worth of 256x256 tiles and a tray of icons —
// so the ceiling only has to cover that, and every byte past it is a byte the next decode can't have.
//
// android gets a share of the app heap, since that's what it's competing for. this is below the 20%
// Coil defaults to, and matches the 15% it already drops to on a device it considers low-memory
internal const val IMAGE_MEMORY_PERCENT = 0.15

// elsewhere a percentage has nothing real to work from — Coil's non-android totalAvailableMemoryBytes
// is a hardcoded 512MB — so those platforms take a flat number instead
internal const val IMAGE_MEMORY_MAX_BYTES = 64L * 1024 * 1024

// platform-specific decoder configuration
expect fun ComponentRegistry.Builder.addPlatformDecoders()

// platform-specific network fetcher (OkHttp on Android/Desktop, Ktor on iOS)
expect fun ComponentRegistry.Builder.addNetworkFetcher()

/** classification of a Coil image load failure — drives Phase 3 retry / negative-cache handling. */
enum class ImageErrorKind {
    RETRYABLE_DECODE,   // HTTP 200 but the body wasn't a decodable image (e.g. go2rtc snapshot not ready)
    RETRYABLE_NETWORK,  // timeout / connection reset / host unreachable
    RETRYABLE_SERVER,   // HTTP 5xx
    PERMANENT_CLIENT,   // HTTP 4xx (bad url / auth)
    UNKNOWN,
}

/**
 * best-effort classification of a Coil load failure. intentionally conservative + message-based for
 * now — phase 1 logs throwable::class so we can learn the real exception taxonomy (OkHttp on
 * Android/Desktop, Ktor on iOS) before hardening this.
 */
fun classifyImageError(t: Throwable): ImageErrorKind {
    val msg = (t.message ?: "").lowercase()
    val name = (t::class.simpleName ?: "").lowercase()
    return when {
        "makefromencoded" in msg || "decode" in name -> ImageErrorKind.RETRYABLE_DECODE
        Regex("http 5\\d\\d").containsMatchIn(msg) -> ImageErrorKind.RETRYABLE_SERVER
        Regex("http 4\\d\\d").containsMatchIn(msg) -> ImageErrorKind.PERMANENT_CLIENT
        name.endsWith("ioexception") || "timeout" in msg || "timed out" in msg ||
            "reset" in msg || "refused" in msg || "unreachable" in msg ||
            "failed to connect" in msg -> ImageErrorKind.RETRYABLE_NETWORK
        else -> ImageErrorKind.UNKNOWN
    }
}

/**
 * whether a failure of this kind should start a cooldown.
 *
 * note this is NOT "is it worth retrying" — a 4xx is the *least* worth retrying and so earns the
 * longest cooldown (see [NegativeImageCache]). gating the cache on retryability let permanently
 * broken urls through unthrottled: a hub weather tile embedding a dead icon url 404'd and was
 * re-fetched on every recomposition, hundreds of times a day.
 *
 * UNKNOWN stays out until the classifier is hardened — suppressing a failure we can't identify
 * risks hiding a working image, which is worse than the re-fetch it would save.
 */
private fun ImageErrorKind.startsCooldown(): Boolean = when (this) {
    ImageErrorKind.RETRYABLE_DECODE, ImageErrorKind.RETRYABLE_NETWORK,
    ImageErrorKind.RETRYABLE_SERVER, ImageErrorKind.PERMANENT_CLIENT -> true
    ImageErrorKind.UNKNOWN -> false
}

/** thrown by [NegativeCacheInterceptor] when a request is short-circuited during its failure cooldown. */
private class ImageCooldownException : Exception("image url in failure cooldown")

/**
 * true if this load "failed" only because the negative cache short-circuited it. call sites with
 * their own onError logging should stay quiet for these — the real failure was already logged once,
 * and re-logging it every recomposition is the noise the cooldown exists to stop.
 */
fun isImageCooldownSkip(t: Throwable?): Boolean = t is ImageCooldownException

/**
 * short-lived negative cache of image URLs that recently failed. a failed Coil load isn't cached
 * (unlike a success, which is served from the disk cache), so without this a broken source — e.g. a
 * go2rtc camera that can't produce a decodable JPEG — is re-fetched on every recomposition.
 *
 * two cooldown shapes, by what the failure says about the url:
 *  - transient (decode / network / 5xx): the source is expected to recover, so back off gradually —
 *    30s, doubling per consecutive failure, capped at 5m.
 *  - permanent (4xx): the server is telling us the url is wrong, and no amount of retrying fixes
 *    that, so go straight to the long cooldown.
 *
 * every entry resets on success, and the hub rotates the `refresh=` param roughly every 15m, which
 * is a new key and gets a fresh attempt regardless of cooldown. so nothing is blocked permanently —
 * a url that starts working is picked back up without needing an invalidation signal we don't have.
 */
private object NegativeImageCache {
    private const val BASE_COOLDOWN_MS = 30_000L
    private const val MAX_COOLDOWN_MS = 5 * 60_000L
    private const val PERMANENT_COOLDOWN_MS = 30 * 60_000L
    private const val MAX_ENTRIES = 64

    private class Entry(var failCount: Int, var lastAttemptMs: Long, var kind: ImageErrorKind)

    private val entries = LruCache<String, Entry>(MAX_ENTRIES)
    private val mutex = Mutex()

    private fun Entry.cooldownMs(): Long =
        if (kind == ImageErrorKind.PERMANENT_CLIENT) {
            PERMANENT_COOLDOWN_MS
        } else {
            val shift = (failCount - 1).coerceIn(0, 4)
            (BASE_COOLDOWN_MS shl shift).coerceAtMost(MAX_COOLDOWN_MS)
        }

    /** true if [url] failed recently and is still within its cooldown window. */
    suspend fun isCoolingDown(url: String): Boolean = mutex.withLock {
        val e = entries[url] ?: return@withLock false
        DateUtils.nowMs() < e.lastAttemptMs + e.cooldownMs()
    }

    suspend fun onFailure(url: String, kind: ImageErrorKind) {
        mutex.withLock {
            val e = entries[url]
            if (e == null) {
                entries.put(url, Entry(failCount = 1, lastAttemptMs = DateUtils.nowMs(), kind = kind))
            } else {
                e.failCount++
                e.lastAttemptMs = DateUtils.nowMs()
                // a url can change verdict (503 while a camera boots, then a steady 404) — the
                // latest failure decides which cooldown shape applies from here
                e.kind = kind
            }
        }
    }

    suspend fun onSuccess(url: String) {
        mutex.withLock { entries.remove(url) }
    }
}

/**
 * Coil interceptor implementing [NegativeImageCache]. runs before fetch/decode, so a short-circuited
 * request never touches the network. cross-platform — covers dashboard, screensaver, dialogs, widget.
 */
private class NegativeCacheInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data as? String
        if (url != null && NegativeImageCache.isCoolingDown(url)) {
            // short-circuit without hitting the network; sentinel throwable keeps it out of the warn log
            return ErrorResult(image = null, request = chain.request, throwable = ImageCooldownException())
        }
        val result = chain.proceed()
        if (url != null) {
            when (result) {
                is SuccessResult -> NegativeImageCache.onSuccess(url)
                is ErrorResult -> {
                    val kind = classifyImageError(result.throwable)
                    if (kind.startsCooldown()) NegativeImageCache.onFailure(url, kind)
                }
            }
        }
        return result
    }
}
