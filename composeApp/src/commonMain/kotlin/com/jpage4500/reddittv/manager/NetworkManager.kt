package com.jpage4500.reddittv.manager

import com.jpage4500.reddittv.util.DateUtils
import com.jpage4500.reddittv.util.LoggerConfig
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException

/**
 * singleton manager for shared network operations (downloads, connectivity)
 */
object NetworkManager {
    private val log = LoggerConfig.createLogger("NetworkManager")

    private val httpClient = createHttpClient(useCookies = false)

    suspend fun downloadUrlToString(
        url: String?,
        headers: Map<String, String>? = null
    ): Result<String> {
        if (url == null) {
            return Result.failure(Exception("url is null"))
        }

        return try {
            val response: HttpResponse = httpClient.get(url) {
                headers?.forEach { (key, value) ->
                    header(key, value)
                }
            }

            if (response.status.isSuccess()) {
                // success/failure logging is handled centrally by HttpLog
                Result.success(response.bodyAsText())
            } else {
                Result.failure(Exception("${response.status}"))
            }
        } catch (e: CancellationException) {
            // never swallow coroutine cancellation - rethrow so it propagates
            throw e
        } catch (e: Exception) {
            log.e { "downloadUrlToString: exception: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * stream a download from url, calling writeChunk for each buffer received.
     * progress is reported via onProgress(downloaded, total) — total is -1 when content-length is unknown.
     * onProgress is throttled to ~50ms to avoid recomposition floods.
     * @return true on success, false on HTTP error or empty response. Cancellation rethrows.
     */
    suspend fun downloadFile(
        url: String,
        expectedSize: Long? = null,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        writeChunk: (buffer: ByteArray, length: Int) -> Unit,
    ): Boolean {
        return try {
            var success = false
            httpClient.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) {
                    log.e { "downloadFile: failed status=${response.status}, url:$url" }
                    return@execute
                }
                val total = response.contentLength() ?: expectedSize ?: -1L
                val channel = response.bodyAsChannel()
                val buf = ByteArray(64 * 1024)
                var downloaded = 0L
                var lastReportMs = 0L
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buf, 0, buf.size)
                    if (read <= 0) break
                    writeChunk(buf, read)
                    downloaded += read
                    val now = DateUtils.nowMs()
                    if (now - lastReportMs >= 50) {
                        onProgress(downloaded, total)
                        lastReportMs = now
                    }
                }
                onProgress(downloaded, total)
                success = downloaded > 0
            }
            success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e { "downloadFile: exception: ${e.message}" }
            false
        }
    }
}
