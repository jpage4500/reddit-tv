package com.jpage4500.reddittv.util

import kotlin.concurrent.Volatile

/**
 * takes a provider's login out of anything logged. a playlist's address carries a username and
 * password in its query, and every stream of an Xtream-style provider carries them in its path —
 * and logs are uploaded (a support report, a link report, a crash).
 *
 * [LoggerConfig] runs every line through [redact] before any writer sees it, so the console,
 * logcat and the log file all get the same text. see docs/Playlists.md
 */
object LogRedactor {
    private const val MASK = "***"

    // shorter than this and masking it would take pieces out of ordinary words
    private const val MIN_SECRET_LENGTH = 4

    // …?username=joe&password=secret
    private val QUERY = Regex("""([?&](?:username|password)=)[^&\s"'<>|]+""", RegexOption.IGNORE_CASE)

    // http://host/live/joe/secret/1234.ts — the same for /movie/ and /series/
    private val XTREAM_PATH = Regex("""(/(?:live|movie|series)/)[^/\s"']+/[^/\s"']+(/\d+)""")

    // http://host:8080/joe/secret/1234.ts — the short form, for a live channel. only with a port:
    // an ordinary link shaped like this (x.com/someone/status/123) never has one
    private val BARE_PATH = Regex("""(://[^/\s"']+:\d+/)[^/\s"'?]+/[^/\s"'?]+(/\d+(?:\.ts|\.m3u8)?)(?=[\s"'?|,]|$)""")

    // the logins of the playlists on this device, masked wherever they turn up
    @Volatile
    private var secrets: List<String> = emptyList()

    /** every username and password in [urls]' queries is masked from here on, in any line */
    fun setSecretsFrom(urls: Collection<String>) {
        secrets = urls.flatMap { url ->
            url.substringAfter('?', "").split('&')
                .filter { it.startsWith("username=", ignoreCase = true) || it.startsWith("password=", ignoreCase = true) }
                .map { it.substringAfter('=') }
        }.filter { it.length >= MIN_SECRET_LENGTH }.distinct()
    }

    fun redact(message: String): String {
        val known = secrets
        val hasUrl = message.contains("://")
        if (!hasUrl && known.isEmpty()) return message
        var text = message
        for (secret in known) {
            if (text.contains(secret)) text = text.replace(secret, MASK)
        }
        if (!hasUrl) return text
        if (text.contains("username=", ignoreCase = true) || text.contains("password=", ignoreCase = true)) {
            text = QUERY.replace(text) { it.groupValues[1] + MASK }
        }
        if (text.contains("/live/") || text.contains("/movie/") || text.contains("/series/")) {
            text = XTREAM_PATH.replace(text) { "${it.groupValues[1]}$MASK/$MASK${it.groupValues[2]}" }
        }
        return BARE_PATH.replace(text) { "${it.groupValues[1]}$MASK/$MASK${it.groupValues[2]}" }
    }
}
