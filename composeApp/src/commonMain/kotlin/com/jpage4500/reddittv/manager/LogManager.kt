package com.jpage4500.reddittv.manager

import com.jpage4500.reddittv.APP_VERSION
import com.jpage4500.reddittv.BUILD_DATE
import com.jpage4500.reddittv.LOGGING_API
import com.jpage4500.reddittv.LOGGING_URL
import com.jpage4500.reddittv.data.Branding
import com.jpage4500.reddittv.manager.LogManager.MAX_UPLOAD_SIZE
import com.jpage4500.reddittv.util.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import reddittv.composeapp.generated.resources.*

private val log = LoggerConfig.createLogger("LogManager")

object LogManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun onAppStart() {
        log.i("**** APP_STARTED: version: $APP_VERSION ****")
        // off the caller's thread: this runs from the platform's process entry point — on android
        // that's Application.onCreate, before any activity exists — and it lists and reads a file per
        // crash. nothing after it waits on the result, so every millisecond it takes is one the user
        // spends looking at a blank window
        scope.launch { handlePendingCrashes() }
    }

    /**
     * @param kind names the upload: "logs" from the Logs screen, "report" for a link the user
     *   reported (ReportManager), so those can be found in the bucket without opening each one
     */
    fun uploadLogs(
        text: String? = null,
        kind: String = "logs",
        listener: ((Result<String>) -> Unit)?
    ) {
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        managerScope.launch {
            if (LOGGING_URL.isEmpty() || LOGGING_API.isEmpty()) {
                log.w("logging URL not set")
                ToastManager.showToast(Res.string.logging_url_not_set, success = false)
                listener?.invoke(Result.failure(Exception("logging not configured")))
                return@launch
            }

            if (text != null) {
                log.i("SUPPORT: $text")
            }
            // write platform data (OS version, device ID, etc) to log
            // android/ios/windows/mac/linux
            val platformName = when (PlatformUtils.getPlatform()) {
                PlatformType.DESKTOP -> PlatformUtils.getPlatformName()
                else -> PlatformUtils.getPlatform().name
            }
            log.i(
                // APP: ANDROID - v1.0.23, 18/36, SM-S942U1, Battery:100%, 2.1GB/7.6GB, 8CPU, +STORE, Build: 2026.08.09 3:24 PM
                "APP: $platformName v$APP_VERSION" +
                    ", OS:${PlatformUtils.getPlatformName()}/${PlatformUtils.getPlatformVersion()}" +
                    ", MODEL:${PlatformUtils.getDeviceModel()}" +
                    ", Battery:${PlatformUtils.getBatteryLevel()}%" +
                    ", ${PlatformUtils.getMemoryInfo()}" +
                    // core count belongs here rather than on every video stats line: it never
                    // changes, and it's what makes those lines' appCpu= (percent of ONE core)
                    // readable in someone else's log
                    ", ${PlatformUtils.getCpuInfo().coreCount}CPU" +
                    (if (PlatformUtils.isPlayStoreInstall()) " +STORE" else "") +
                    (if (PlatformUtils.isTablet()) " +TABLET" else "") +
                    ", Build:$BUILD_DATE"
            )

            log.i("--------------------------------------------")

            val dateStr = DateUtils.formatDate("yyyyMMdd-HHmm")
            val platformLc = platformName.lowercase()

            // read log file into string (plus the tail of the rotated log, if it's recent)
            // flush first: AppLogWriter batches lines, so the newest aren't on disk yet
            flushLogFile()
            val fileStr = StorageManager.readFile(PATH_LOGS, FILE_LOG)
            fileStr?.let {
                // the `logs` bucket is shared with the other apps and every upload is x-upsert, so
                // an un-prefixed "android-logs-<minute>.txt" can overwrite another app's report.
                // the prefix is Branding.analyticsId, which is the id the bucket's other writers
                // already go by
                val fileName = "${Branding.analyticsId}-${platformLc}-$kind-$dateStr.txt"
                val result = uploadLog(fileName, withRotatedLog(fileStr).encodeToByteArray())
                if (!result.isSuccess) {
                    listener?.invoke(result)
                    return@launch
                }
            }

            listener?.invoke(Result.success("success"))
        }
    }

    // max size of an uploaded log file. approximate - counted in characters, but log files are
    // effectively ascii so 1 char = 1 byte
    private const val MAX_UPLOAD_SIZE = 3 * 1024 * 1024

    // only include the rotated log if it was written within this window; older than that and it
    // holds nothing relevant to whatever is being reported now
    private const val MAX_ROTATED_AGE_MS = 12 * 60 * 60 * 1000L

    /**
     * the log file rotates at 2MB, which means the entries leading up to a problem are often in
     * FILE_BACKUP and would never be uploaded - worst case the current log is nearly empty because
     * it just rotated. prepend the tail of the rotated log when it's recent enough to be useful,
     * keeping the combined size under [MAX_UPLOAD_SIZE].
     */
    private fun withRotatedLog(fileStr: String): String {
        val budget = MAX_UPLOAD_SIZE - fileStr.length
        if (budget <= 0) return fileStr

        val metadata = StorageManager.getFileDetails(PATH_LOGS, FILE_BACKUP) ?: return fileStr
        val ageMs = DateUtils.nowMs() - metadata.lastModified
        if (ageMs > MAX_ROTATED_AGE_MS) {
            log.d("withRotatedLog: skipping $FILE_BACKUP, age: ${ageMs / 1000 / 60} mins")
            return fileStr
        }

        val rotatedStr = StorageManager.readFile(PATH_LOGS, FILE_BACKUP) ?: return fileStr
        // keep the newest entries and drop the leading partial line
        val tail = when {
            rotatedStr.length <= budget -> rotatedStr
            else -> rotatedStr.takeLast(budget).substringAfter('\n', "")
        }
        if (tail.isEmpty()) return fileStr

        log.d("withRotatedLog: including ${tail.length} of ${rotatedStr.length} chars from $FILE_BACKUP")
        return "$tail\n----- log rotated -----\n$fileStr"
    }

    suspend fun uploadLog(fileName: String, fileData: ByteArray): Result<String> {
        try {
            val httpClient = createHttpClient(useCookies = false)
            val url = "$LOGGING_URL/storage/v1/object/logs/$fileName"
            log.d("uploadLogs: uploading to $url, size: ${fileData.size} bytes")
            val response = httpClient.post(url) {
                headers {
                    append("Authorization", "Bearer $LOGGING_API")
                    append("apikey", LOGGING_API)
                    append("Content-Type", "text/plain")
                    append("x-upsert", "true")
                }
                setBody(fileData)
            }

            if (response.status.isSuccess()) {
                log.d("uploadLogs: file uploaded")
                return Result.success(response.bodyAsText())
            } else {
                log.e("uploadLogs: upload failed: ${response.status}")
                return Result.failure(Exception("upload failed: ${response.status}"))
            }
        } catch (e: Exception) {
            log.e("uploadLogs: Exception: ${e.message}")
            return Result.failure(e)
        }
    }

    const val PATH_CRASHES = "crashes"

    // called by platform handlers when an uncaught exception is detected.
    // MUST be synchronous — process is about to die and async writes can't be trusted to flush.
    // never throws: a failure here is preferable to masking the original throwable.
    fun recordCrash(throwable: Throwable, threadName: String) {
        try {
            val timestamp = DateUtils.formatDate("yyyyMMdd-HHmmss")
            val fileName = "crash-$timestamp.txt"
            val content = buildString {
                appendLine("CRASH at $timestamp")
                appendLine("APP: $APP_VERSION")
                appendLine("PLATFORM: ${PlatformUtils.getPlatform()} ${PlatformUtils.getPlatformVersion()}")
                appendLine("THREAD: $threadName")
                appendLine("EXCEPTION: ${throwable::class.simpleName}: ${throwable.message}")
                appendLine("STACK:")
                append(throwable.stackTraceToString())
            }
            StorageManager.writeFileSync(PATH_CRASHES, fileName, content)

            // also log normally to try and note where the crash occured
            log.e("CRASH: ${throwable::class.simpleName}: ${throwable.message}", throwable)
            // the process is about to die - get the batched lines onto disk while we still can
            flushLogFile()
        } catch (_: Throwable) {
            // swallow — re-throw of the original exception is the priority
        }
    }

    // called from LogManager.onAppStart(), off the main thread. appends each pending crash directly
    // to the standard log file (synchronous *here*, so the uploadLogs below is guaranteed to see
    // them), shows a toast, then triggers upload. crash files are deleted only on upload success so a
    // failed upload retries on the next launch.
    private fun handlePendingCrashes() {
        val files = StorageManager.listFiles(PATH_CRASHES)
        if (files.isEmpty()) return

        log.w("handlePendingCrashes: ${files.size} crash file(s) from previous session")

        for (name in files) {
            val content = StorageManager.readFile(PATH_CRASHES, name) ?: continue
            val block = "\n----- CRASH from previous session ($name) -----\n$content\n"
            StorageManager.writeFileSync(PATH_LOGS, FILE_LOG, block, append = true)
        }
        // these bypassed AppLogWriter, so its cached log size no longer matches the file
        invalidateLogFileSize()

        ToastManager.showToast(Res.string.reporting_crash, success = false)

        uploadLogs(text = "CRASH", listener = { result ->
            if (result.isSuccess) {
                files.forEach { StorageManager.deleteFile(PATH_CRASHES, it) }
            } else {
                log.w("handlePendingCrashes: upload failed, keeping crash files for retry")
            }
        })
    }
}


