package com.jpage4500.reddittv.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.jpage4500.reddittv.data.AppPref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.*
import kotlin.time.Duration


public const val NO_TEMP = "--"

/**
 * utility functions for common operations
 */
object Utils {
    private val log = LoggerConfig.createLogger("Utils")

    /**
     * format duration to human-readable string
     * @return formatted string like "30 secs" or "2 hours"
     */
    fun formatDuration(duration: Duration): String {
        return when {
            duration.inWholeHours > 0 -> "${duration.inWholeHours} ${if (duration.inWholeHours == 1L) "hour" else "hours"}"
            duration.inWholeMinutes > 0 -> "${duration.inWholeMinutes} ${if (duration.inWholeMinutes == 1L) "min" else "mins"}"
            else -> "${duration.inWholeSeconds} secs"
        }
    }

    /**
     * format duration in milliseconds to human-readable string
     * @return formatted string like "1m 30s" or "500ms"
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs < 1000) {
            return "${durationMs}ms"
        }

        val seconds = durationMs / 1000
        if (seconds < 60) {
            val ms = durationMs % 1000
            return if (ms > 0) "${seconds}s ${ms}ms" else "${seconds}s"
        }

        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        if (minutes < 60) {
            return if (remainingSeconds > 0) "${minutes}m ${remainingSeconds}s" else "${minutes}m"
        }

        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (remainingMinutes > 0) "${hours}h ${remainingMinutes}m" else "${hours}h"
    }

    /**
     * format a byte count to a human-readable string, ie: "512B", "4.5KB", "1.2MB"
     */
    fun bytesToDisplayString(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${kb.formatDecimal(1)}KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${mb.formatDecimal(1)}MB"
        return "${(mb / 1024.0).formatDecimal(1)}GB"
    }

    /**
     * find the first URL in the given text
     */
    fun findUrl(text: String?): String? {
        if (text == null) return null
        // a regex pattern for matching URLs that excludes common delimiters like quotes and angle brackets
        // matches http://, https://, ftp://, or rtsp:// followed by valid URL characters
        val urlPattern = "(?:https?|ftp|rtsp)://[^\\s\"'<>)\\]]*"
        val regex = Regex(urlPattern)

        // use find() to get the first match in the input string
        val matchResult = regex.find(text)

        // return the value of the first match, or null if no URL is found
        val url = matchResult?.value
        //log.d("findUrl: found url=$url")
        return url
    }

    /**
     * when a signed link stops working, by its own query: exp=, expires=, expiry= (Akamai's
     * hdnts=exp=…~acl=… too), TikTok's expire= and x-expires=. epoch seconds, or ms when it's 13 digits. null when it doesn't say.
     * a host often answers an expired link with a redirect somewhere else (google.com) rather than
     * an error, so the player sees a page where a playlist should be
     */
    fun linkExpiryMs(url: String): Long? {
        val value = LINK_EXPIRY.find(url.substringAfter('?', ""))?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return if (value >= 1_000_000_000_000L) value else value * 1000
    }

    /**
     * a link signed for whoever asked for it: a token, a signature or an expiry in its query. one
     * like this stops working after a while, where a plain file's link doesn't
     */
    fun isSignedLink(url: String): Boolean =
        linkExpiryMs(url) != null || SIGNED_LINK.containsMatchIn(url.substringAfter('?', ""))

    private val SIGNED_LINK = Regex(
        """(?:^|&)(?:token|sig|signature|hdnts|hdntl|auth|Policy|Key-Pair-Id|X-Amz-Signature|md5|st)=""",
        RegexOption.IGNORE_CASE,
    )

    private val LINK_EXPIRY = Regex(
        """(?:^|[&~])(?:exp|expire|expires|expiry|x-expires)=(\d{10}|\d{13})(?:$|[&~])""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * convert optional string to optional enum value
     * @param name the enum name to convert, or null
     * @return the enum value, or null if name is null or invalid
     */
    inline fun <reified T : Enum<T>> enumFromName(name: String?): T? {
        if (name == null) return null
        return try {
            enumValueOf<T>(name)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * a remote (TV) or a keyboard (desktop) drives the app, so something always has focus: screens
     * put it on their first item, and arrows move it. a touch screen leaves focus alone — a focused
     * item there would just look selected
     */
    fun isFocusDriven(): Boolean = PlatformUtils.isTv() || PlatformUtils.getPlatform() == PlatformType.DESKTOP

    fun isPlatform(vararg platformArr: PlatformType): Boolean {
        return platformArr.contains(PlatformUtils.getPlatform())
    }

    fun isMobilePlatform(): Boolean {
        return isPlatform(PlatformType.IOS, PlatformType.ANDROID)
    }

    /**
     * @return true if the given text contains some common HTML tags
     */
    fun hasHtmlText(text: String?): Boolean {
        return text?.containsAny(true, "<html", "<div", "<table", "<img", "<iframe", "<span", "<ul", "<head", "<body", "<br", "<h1", "<h2", "<b", "<a ", "<u", "<style") ?: false
    }

    fun isMainThread(): Boolean {
        return Dispatchers.Main.immediate.isDispatchNeeded(EmptyCoroutineContext)
    }

    fun runOnMainThread(function: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            function()
        }
    }

    fun celsiusToFahrenheit(celsius: Float): Float {
        return (celsius * 9f / 5f) + 32f
    }

    /**
     * Float to Temp string (rounded with ° symbol, or "--" if null)
     */
    fun floatToTemp(temp: Float?): String {
        if (temp == null) return NO_TEMP
        return temp.roundToInt().toString() + "°"
    }

    /**
     * check if a color is dark
     * returns true if the color's luminance is less than 0.4
     */
    fun isColorDark(color: Color): Boolean {
        if (color == Color.Unspecified) return false
        return color.luminance() < 0.4f
    }

    fun convertHexToArgb(hex: String): String {
        // 1. Remove the '#' if it exists
        val cleanHex = hex.removePrefix("#")

        // 2. Prepend 'FF' for the Alpha channel (fully opaque)
        return "FF$cleanHex"
    }

    /**
     * transparency is shown to the user as a percentage (0% = fully opaque, 100% = fully transparent)
     * but stored as an alpha value 0-255 (255 = fully opaque). these convert between the two.
     */
    fun alphaToTransparencyPercent(alpha: Int): Int {
        return ((255 - alpha) / 255f * 100f).roundToInt().coerceIn(0, 100)
    }

    fun transparencyPercentToAlpha(percent: Int): Int {
        return (255 - percent / 100f * 255f).roundToInt().coerceIn(0, 255)
    }

    /**
     * Converts Latitude and Longitude to an OpenStreetMap tile URL.
     * * @param lat Latitude of the location
     * @param lon Longitude of the location
     * @param zoom Zoom level (typically 0 to 19, where 19 is closest)
     * @return The URL string of the map tile image
     */
    fun getOpenStreetMapUrl(lat: Double?, lon: Double?, zoom: Int): String {
        if (lat == null || lon == null) return "?"
        val latRad = lat * PI / 180.0
        val n = 2.0.pow(zoom)

        val xTile = ((lon + 180.0) / 360.0 * n).toInt()
        val yTile = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()

        // Construct the URL using OSM's standard tile server format
        return "https://tile.openstreetmap.org/$zoom/$xTile/$yTile.png"
    }

    /**
     * Generates an official Google Maps URL for a specific location.
     * * @param lat The latitude of the location
     * @param lng The longitude of the location
     * @return A formatted URL string that places a pin on the coordinates
     */
    fun getGoogleMapsUrl(lat: Double?, lng: Double?): String {
        if (lat == null || lng == null) return "?"
        return "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
    }

    /**
     * Build a Google Static Maps URL with a single red marker at lat/lng.
     * Returns null if lat/lng are null or apiKey is blank.
     */
    fun getGoogleStaticMapUrl(
        lat: Double?, lng: Double?, zoom: Int,
        apiKey: String,
        width: Int = 600, height: Int = 600,
    ): String? {
        if (lat == null || lng == null || apiKey.isBlank()) return null
        return "https://maps.googleapis.com/maps/api/staticmap" +
                "?center=$lat,$lng" +
                "&zoom=$zoom" +
                "&size=${width}x${height}" +
                "&maptype=roadmap" +
                "&markers=color:red%7C$lat,$lng" +
                "&key=$apiKey"
    }

    // Wikimedia only serves a fixed set of thumbnail widths for direct (hotlinked) requests; any
    // other width is rejected with HTTP 400. sources feeding us thumb URLs (Bing image search,
    // HTML content) routinely uses arbitrary widths (e.g. 800/1200px),
    // so we snap the width up to the nearest size Wikimedia will actually serve.
    // see https://www.mediawiki.org/wiki/Common_thumbnail_sizes
    private val WIKIMEDIA_THUMB_SIZES = intArrayOf(20, 40, 60, 120, 250, 330, 500, 960, 1280, 1920, 3840)
    private val wikimediaThumbWidthRegex = Regex("""^(\d+)px-""")

    /**
     * rewrite an upload.wikimedia.org thumbnail URL so its width is one Wikimedia will serve.
     * the width lives in the last path segment as a "<width>px-<filename>" prefix; if it isn't a
     * standard size we round it up to the nearest one (capped at the largest). non-Wikimedia and
     * non-thumb URLs pass through unchanged.
     */
    fun fixWikimediaThumbUrl(url: String): String {
        if (!url.contains("upload.wikimedia.org") || !url.contains("/thumb/")) return url
        val lastSlash = url.lastIndexOf('/')
        if (lastSlash < 0) return url
        val segment = url.substring(lastSlash + 1)
        val match = wikimediaThumbWidthRegex.find(segment) ?: return url
        val width = match.groupValues[1].toIntOrNull() ?: return url
        if (width in WIKIMEDIA_THUMB_SIZES) return url
        val standard = WIKIMEDIA_THUMB_SIZES.firstOrNull { it >= width } ?: WIKIMEDIA_THUMB_SIZES.last()
        log.v { "fixWikimediaThumbUrl: rewrote ${width}px -> ${standard}px" }
        return url.substring(0, lastSlash + 1) + "${standard}px-" + segment.substring(match.value.length)
    }

    /**
     * [text] with its HTML entities decoded: named ones (`&amp;`, `&rsquo;`…) and numeric ones
     * (`&#183;`, `&#xb7;`) — including code points past U+FFFF, which take two chars (`&#x1f60c;` is
     * an emoji). one it doesn't know is left as written
     */
    fun decodeHtmlEntities(text: String): String {
        if (!text.contains('&')) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '&') {
                val semi = text.indexOf(';', i)
                if (semi > i + 1 && semi - i <= 10) {
                    val decoded = decodeEntity(text.substring(i + 1, semi))
                    if (decoded != null) {
                        sb.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun decodeEntity(entity: String): String? {
        if (!entity.startsWith("#")) return HTML_ENTITIES[entity] ?: HTML_ENTITIES[entity.lowercase()]
        val number = entity.substring(1)
        val code = (if (number.startsWith("x") || number.startsWith("X")) number.substring(1).toIntOrNull(16) else number.toIntOrNull())
            ?: return null
        return when {
            code <= 0 || code > 0x10FFFF || code in 0xD800..0xDFFF -> null
            code < 0x10000 -> code.toChar().toString()
            // a surrogate pair
            else -> {
                val offset = code - 0x10000
                charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar()).concatToString()
            }
        }
    }

    private val HTML_ENTITIES: Map<String, String> = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "copy" to "©", "reg" to "®", "trade" to "™",
        "hellip" to "…", "mdash" to "—", "ndash" to "–", "lsquo" to "‘",
        "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "bull" to "•",
        "middot" to "·", "deg" to "°", "plusmn" to "±", "sup2" to "²", "sup3" to "³",
        "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "iexcl" to "¡",
        "iquest" to "¿", "laquo" to "«", "raquo" to "»",
    )
}
