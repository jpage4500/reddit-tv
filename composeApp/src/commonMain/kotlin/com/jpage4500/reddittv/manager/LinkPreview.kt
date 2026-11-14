package com.jpage4500.reddittv.manager

import com.jpage4500.reddittv.manager.HttpLog.expectRefusal
import com.jpage4500.reddittv.manager.HttpLog.quiet
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.Utils
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

private val log = LoggerConfig.createLogger("LinkPreview")

/**
 * the picture a link unfurls to — its page's og:image (or twitter:image) — for link posts reddit
 * gave no thumbnail (r/programming turns thumbnails off). what reddit's own page shows for them.
 *
 * read on demand as a tile shows, a few at a time, from the start of the page only (the meta tags
 * are in its <head>), and kept for the session: a page without one is remembered as such
 */
object LinkPreview {
    // pages read at once
    private const val MAX_CONCURRENT = 4

    // how much of a page is read looking for the tags
    private const val MAX_BYTES = 256L * 1024

    // pages remembered; the oldest are dropped past this
    private const val CACHE_SIZE = 500

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

    // <meta property="og:image" content="…">, the attributes in either order
    private val META = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val META_KEY = Regex("""(?:property|name)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val META_CONTENT = Regex("""content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val IMAGE_KEYS = listOf("og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src")

    private val httpClient = createHttpClient(useCookies = false)
    private val gate = Semaphore(MAX_CONCURRENT)
    private val lock = Mutex()

    // url → image, "" for a page that has none
    private val cache = LinkedHashMap<String, String>()

    /** the image already found for [url]; null when none, or not looked for yet */
    fun cached(url: String): String? = cache[url]?.ifEmpty { null }

    /** [url]'s preview image, or null */
    suspend fun imageFor(url: String): String? {
        if (!isFetchable(url)) return null
        lock.withLock { cache[url] }?.let { return it.ifEmpty { null } }
        val image = gate.withPermit {
            // another tile may have read it while this one waited
            lock.withLock { cache[url] } ?: fetch(url)
        }
        lock.withLock {
            cache[url] = image
            if (cache.size > CACHE_SIZE) cache.remove(cache.keys.first())
        }
        return image.ifEmpty { null }
    }

    private fun isFetchable(url: String): Boolean {
        if (!url.startsWith("http")) return false
        val host = runCatching { Url(url).host.lowercase() }.getOrNull() ?: return false
        // a link back to reddit is a post or a community: nothing to unfurl
        return !host.endsWith("reddit.com") && !host.endsWith("redd.it")
    }

    // "" when there's no image (or the page couldn't be read): remembered so it isn't asked again
    private suspend fun fetch(url: String): String = try {
        httpClient.prepareGet(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "text/html")
            quiet()
            expectRefusal()
        }.execute { response ->
            val type = response.contentType()
            if (!response.status.isSuccess() || (type != null && !type.match(ContentType.Text.Html))) return@execute ""
            val head = response.bodyAsChannel().readRemaining(MAX_BYTES).readText().substringBefore("</head>")
            findImage(head, response.request.url.toString()) ?: ""
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.d("fetch: $url: ${e.message}")
        ""
    }

    /** the first image tag in [html], made absolute against [pageUrl] */
    private fun findImage(html: String, pageUrl: String): String? {
        val tags = META.findAll(html).mapNotNull { tag ->
            val key = META_KEY.find(tag.value)?.groupValues?.get(1)?.lowercase() ?: return@mapNotNull null
            val content = META_CONTENT.find(tag.value)?.groupValues?.get(1) ?: return@mapNotNull null
            key to content
        }.toMap()
        val raw = IMAGE_KEYS.firstNotNullOfOrNull { tags[it] }?.trim() ?: return null
        val image = Utils.decodeHtmlEntities(raw)
        return when {
            image.startsWith("http") -> image
            image.startsWith("//") -> "https:$image"
            else -> runCatching { URLBuilder(pageUrl).takeFrom(image).buildString() }.getOrNull()
        }?.takeIf { it.startsWith("http") }
    }
}
