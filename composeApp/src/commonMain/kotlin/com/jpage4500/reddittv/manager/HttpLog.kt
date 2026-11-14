package com.jpage4500.reddittv.manager

import com.jpage4500.reddittv.manager.HttpLog.shouldLogBody
import co.touchlab.kermit.Severity
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.Utils
import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException

/**
 * single place that logs ALL HTTP requests/responses. every client built by [createHttpClient]
 * routes through here (via [installHttpLog]), so filtering logs by the "HttpLog" tag shows the whole
 * network conversation with consistent formatting.
 *
 * each request is one line, logged when it completes: method, time, URL and both bodies.
 * everything here is verbose (verbose is on while Debug Mode is), except errors and failures.
 *
 * bodies are only captured for small, textual responses — see [shouldLogBody]. this matters because
 * the same client streams large downloads (firmware) and loads images (Coil); the ResponseObserver
 * filter runs BEFORE Ktor splits the response channel, so those never get buffered.
 */
object HttpLog {
    private val log = LoggerConfig.createLogger("HttpLog")

    // per-request id (put on the request, read back on the response) for req/resp correlation
    private val reqNumKey = AttributeKey<Int>("HttpLogReqNum")

    // the request's body as it appears on the line logged at completion (", >>(1.2KB):"…"")
    private val reqBodyKey = AttributeKey<String>("HttpLogReqBody")

    // set on a request whose 403 / 404 is an answer, not an error (ie: DIAL's "this app isn't
    // installed", or "it is, but it won't talk to you"), so it logs as verbose. see [expectRefusal]
    private val refusalExpectedKey = AttributeKey<Boolean>("HttpLogRefusalExpected")

    /** a 403 or 404 on this request is expected — log it as verbose rather than a warning */
    fun HttpRequestBuilder.expectRefusal() = attributes.put(refusalExpectedKey, true)

    // set on a request that's repeated every second or so (the Now Playing poll). see [quiet]
    private val quietKey = AttributeKey<Boolean>("HttpLogQuiet")

    /** this request is a poll: only log it when it fails */
    fun HttpRequestBuilder.quiet() = attributes.put(quietKey, true)

    // request/response bodies are truncated to this many chars in the log
    private const val LOG_BODY_SIZE = 5000

    // only capture response bodies at/under this size (skip large payloads even when textual)
    private const val MAX_BODY_BYTES = 20_000L

    // query-param names whose values are redacted in logged URLs (case-insensitive) — most
    // public APIs pass their key this way.
    private val sensitiveParams = setOf("access_token", "token", "client_id", "key", "apikey", "api_key", "password", "secret")

    private var counter = 0
    private fun nextNum(): Int = ++counter

    // bodies are verbose lines like the rest; when verbose is off a response body isn't even read
    // for them. [shouldLogBody] runs this on every response, so it's the logger's cached config
    private fun logBodies(): Boolean = log.config.minSeverity <= Severity.Verbose

    /**
     * assign + record this request's id, and keep its body for the response line — nothing is
     * logged until the request completes. [content] is the rendered request body.
     */
    fun logRequest(request: HttpRequestBuilder, content: Any?) {
        request.attributes.put(reqNumKey, nextNum())
        if (!logBodies() || request.attributes.contains(quietKey)) return
        // at onRequest time a String body set via setBody() may still be a raw String (not yet a
        // TextContent) — handle both. this is the outgoing body, so reading it consumes nothing.
        val text = when (content) {
            is TextContent -> content.text
            is String -> content
            else -> null
        }
        if (!text.isNullOrEmpty()) request.attributes.put(reqBodyKey, ", ${formatBody(">>", text)}")
    }

    /**
     * log the request's one line from the response's metadata alone. a response whose body will be
     * logged ([shouldLogBody]) is skipped here: [logResponseBody] logs its line once the body has
     * been read, so the whole exchange shares one line.
     */
    fun logResponse(response: HttpResponse) {
        if (shouldLogBody(response)) return
        logResponseLine(response, response.contentLength()?.let { ", <<(${Utils.bytesToDisplayString(it)})" } ?: "")
    }

    fun logFailure(request: HttpRequestBuilder, cause: Throwable) {
        // a cancelled request isn't a failure and isn't logged at all: the location search drops its
        // in-flight lookup on every keystroke, and a line per character buries what does matter
        if (cause is CancellationException) return
        val tag = tag(request.attributes.getOrNull(reqNumKey))
        val reqBody = request.attributes.getOrNull(reqBodyKey) ?: ""
        log.e("$tag ${request.method.value} FAILED: ${sanitizeUrl(request.url.buildString())}$reqBody : ${cause.message}")
    }

    /**
     * the request's one line: "(N) POST: 837ms url, >>(1.2KB):"request", <<(23.0KB):"response"".
     * [respPart] is the response's size, with its body when there is one.
     */
    private fun logResponseLine(response: HttpResponse, respPart: String) {
        val call = response.call
        val tag = tag(call.attributes.getOrNull(reqNumKey))
        val method = call.request.method.value
        val ms = response.responseTime.timestamp - response.requestTime.timestamp
        val url = sanitizeUrl(call.request.url.toString())
        val reqBody = call.attributes.getOrNull(reqBodyKey) ?: ""
        if (response.status.isSuccess() && call.attributes.contains(quietKey)) {
            return
        } else if (response.status.isSuccess() || response.status == HttpStatusCode.SwitchingProtocols) {
            log.v { "$tag $method: ${ms}ms $url$reqBody$respPart" }
        } else if (isExpectedRefusal(response)) {
            // too chatty
            //log.v { "$tag $method REFUSED ${response.status.value}: ${ms}ms $url" }
        } else {
            log.w("$tag $method HTTP_ERROR ${response.status.value}: ${ms}ms $url$reqBody$respPart")
        }
    }

    // only observe a response body for logging when verbose is on and it's textual (see class doc
    // — this gate runs before the channel is split, so streaming/binary is left untouched). servers
    // often reply chunked with no Content-Length; allow those, and cap only when a length is actually
    // known — the textual content-type check keeps big binary downloads out.
    fun shouldLogBody(response: HttpResponse): Boolean {
        if (!logBodies()) return false
        // never read a websocket/upgrade body — doing so consumes the live socket and closes it
        if (response.status == HttpStatusCode.SwitchingProtocols) return false
        // an expected refusal's "Not Found" / "Forbidden" page says nothing its status didn't
        if (isExpectedRefusal(response)) return false
        if (response.call.attributes.contains(quietKey)) return false
        if (!isServerHost(response.call.request.url.host)) return false
        if (!isTextual(response.contentType())) return false
        // no Content-Length (chunked) — can't cap by size up front; logResponseBody caps the read instead
        val len = response.contentLength() ?: return true
        return len in 1..MAX_BODY_BYTES
    }

    // hosts whose traffic is logged verbosely. this starts as "log everything"; narrow it to our
    // own receivers' hosts if stream and CDN traffic makes the logs noisy.
    private fun isServerHost(@Suppress("UNUSED_PARAMETER") host: String): Boolean = true

    /** log the line for a response [logResponse] skipped, with its body */
    suspend fun logResponseBody(response: HttpResponse) {
        val body = runCatching { readLoggablePrefix(response) }.getOrNull()
        val respPart = if (!body.isNullOrEmpty()) ", ${formatBody("<<", body)}"
        else response.contentLength()?.let { ", <<(${Utils.bytesToDisplayString(it)})" } ?: ""
        logResponseLine(response, respPart)
    }

    // retain only the first [MAX_BODY_BYTES] for the log, but keep draining the rest: the observer
    // reads a split copy of the channel, so leaving bytes unread would backpressure-stall the real
    // consumer. chunked responses carry no Content-Length, so shouldLogBody
    // can't cap them by size — this is where the cap is actually enforced for those.
    private suspend fun readLoggablePrefix(response: HttpResponse): String {
        val channel = response.bodyAsChannel()
        val head = ByteArray(MAX_BODY_BYTES.toInt())
        val buf = ByteArray(16 * 1024)
        var kept = 0
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buf, 0, buf.size)
            if (read <= 0) break
            if (kept < head.size) {
                val take = minOf(read, head.size - kept)
                buf.copyInto(head, kept, 0, take)
                kept += take
            }
        }
        return head.decodeToString(0, kept)
    }

    // a 403 / 404 on a request marked with [expectRefusal]
    private fun isExpectedRefusal(response: HttpResponse): Boolean =
        (response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.Forbidden) &&
            response.call.attributes.contains(refusalExpectedKey)

    private fun tag(n: Int?): String = if (n != null) "($n)" else "(?)"

    /**
     * a body as it appears on the request's line: "<arrow>(<size>):"<body>"", truncated to
     * [LOG_BODY_SIZE] and with newlines flattened to "⏎ " so the line stays one line.
     */
    private fun formatBody(arrow: String, body: String): String {
        val shown = if (body.length <= LOG_BODY_SIZE) body else body.take(LOG_BODY_SIZE) + "…"
        return "$arrow(${Utils.bytesToDisplayString(body.length.toLong())}):\"${shown.replace("\n", "⏎ ")}\""
    }

    // only log bodies for textual content types (skip images/binary that also flow through here)
    private fun isTextual(ct: ContentType?): Boolean {
        if (ct == null) return true
        return ct.match(ContentType.Text.Any) ||
            ct.match(ContentType.Application.Json) ||
            ct.match(ContentType.Application.Xml) ||
            ct.match(ContentType.Application.FormUrlEncoded)
    }

    /**
     * redact secrets from a URL before logging: any sensitive query-param value (api keys, etc).
     */
    private fun sanitizeUrl(url: String): String {
        val qIdx = url.indexOf('?')
        if (qIdx == -1) return url
        val base = url.substring(0, qIdx + 1)
        val query = url.substring(qIdx + 1)
        val redacted = query.split('&').joinToString("&") { pair ->
            val eq = pair.indexOf('=')
            if (eq == -1) return@joinToString pair
            val name = pair.substring(0, eq)
            if (name.lowercase() in sensitiveParams) "$name=***" else pair
        }
        return base + redacted
    }
}

/**
 * install the shared [HttpLog] into a client config: each request's line and failures via a small
 * plugin. while verbose is on, a small textual response's line is logged from
 * [ResponseObserver] instead, with its body (read from a split copy so the caller's body isn't
 * consumed).
 */
fun HttpClientConfig<*>.installHttpLog() {
    install(httpLogPlugin)
    install(ResponseObserver) {
        filter { call -> HttpLog.shouldLogBody(call.response) }
        onResponse { response -> HttpLog.logResponseBody(response) }
    }
}

private val httpLogPlugin = createClientPlugin("HttpLog") {
    onRequest { request, content ->
        HttpLog.logRequest(request, content)
    }
    on(Send) { request ->
        try {
            val call = proceed(request)
            HttpLog.logResponse(call.response)
            call
        } catch (cause: Throwable) {
            HttpLog.logFailure(request, cause)
            throw cause
        }
    }
}
