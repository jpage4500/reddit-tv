package com.jpage4500.reddittv.util

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

const val PATH_LOGS = "logs"

// stamped in front of every logger tag, so app lines stand out in a shared console (logcat, Xcode).
// LogsDialog strips it back out for display — keep the two in sync.
const val LOG_TAG_PREFIX = "RTV+"
const val FILE_LOG = "reddit-tv.log"
const val FILE_BACKUP = "reddit-tv.log.old"
private const val MAX_LOG_SIZE = 2 * 1024 * 1024 // 2 MB

// how long the writer holds off after an append so the next burst coalesces into one write
private const val FLUSH_DELAY_MS = 250L

// caps on a single append, so a flood of logging doesn't build one huge string
private const val MAX_BATCH_LINES = 256
private const val MAX_BATCH_CHARS = 64 * 1024

// verbose lines written to FILE_LOG are cut to this many characters
private const val MAX_VERBOSE_FILE_CHARS = 400

// formatted log lines waiting to be appended to FILE_LOG. writing each line as it arrived meant
// an open/append/close of the file per log line; [startLogFileWriter] batches them instead
private val logQueue = Channel<String>(Channel.UNLIMITED)
private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private var isLogFileWriterStarted = false

// how much FILE_LOG must grow before a failed rotation is retried
private const val ROTATE_RETRY_SIZE = 64 * 1024

// running size of FILE_LOG, avoiding a directory listing on every log line.
// -1 means unknown - the next write re-reads the real size from disk.
// only ever touched from StorageManager's single-threaded writeScope, so no locking is needed.
// counts characters rather than utf-8 bytes, so it can undercount slightly for non-ascii
// messages; it becomes exact again on every rotation.
private var logFileSize = -1L

/**
 * invalidate [logFileSize] after something writes to FILE_LOG outside of [AppLogWriter]
 * (ie: LogManager appending crash reports via writeFileSync)
 */
fun invalidateLogFileSize() {
    logFileSize = -1L
}

/**
 * start the single coroutine that appends buffered log lines to FILE_LOG.
 * called once from [LoggerConfig.initialize]
 */
fun startLogFileWriter() {
    if (isLogFileWriterStarted) return
    isLogFileWriterStarted = true
    logScope.launch {
        while (true) {
            // wake on the first line so an idle app still writes it right away
            val batch = StringBuilder(logQueue.receive())
            var lines = 1
            while (lines < MAX_BATCH_LINES && batch.length < MAX_BATCH_CHARS) {
                val next = logQueue.tryReceive().getOrNull() ?: break
                batch.append(next)
                lines++
            }
            val content = batch.toString()
            // StorageManager serializes writes on its own thread; rotate once this one lands
            StorageManager.writeFile(PATH_LOGS, FILE_LOG, content, append = true) { isOk ->
                if (isOk) checkRotateLog(content.length)
            }
            // let the next burst pile up in the queue rather than writing a file per line
            delay(FLUSH_DELAY_MS)
        }
    }
}

/**
 * append anything still buffered to FILE_LOG now, on the calling thread. call this before reading
 * the log file (upload, log viewer) and from the crash handler — [startLogFileWriter] otherwise
 * holds lines for up to [FLUSH_DELAY_MS]
 */
fun flushLogFile() {
    val batch = StringBuilder()
    while (true) {
        val next = logQueue.tryReceive().getOrNull() ?: break
        batch.append(next)
    }
    if (batch.isEmpty()) return
    StorageManager.writeFileSync(PATH_LOGS, FILE_LOG, batch.toString(), append = true)
    // bypassed the writer coroutine, so its running size no longer matches the file
    invalidateLogFileSize()
}

/**
 * rotate FILE_LOG to FILE_BACKUP once it grows past MAX_LOG_SIZE.
 * called after each successful write, on StorageManager's single write thread.
 * NOTE: can't use a Logger here - logging from the log writer would recurse
 */
private fun checkRotateLog(charsWritten: Int) {
    logFileSize = if (logFileSize < 0) {
        // unknown - first write of the session or invalidated; disk size already includes this write
        StorageManager.getFileDetails(PATH_LOGS, FILE_LOG)?.size ?: 0L
    } else {
        logFileSize + charsWritten
    }
    if (logFileSize <= MAX_LOG_SIZE) return

    // delete any previous backup first - renaming over an existing file fails on iOS and windows
    StorageManager.deleteFile(PATH_LOGS, FILE_BACKUP)
    logFileSize = if (StorageManager.renameFile(PATH_LOGS, FILE_LOG, FILE_BACKUP)) {
        0L
    } else {
        // back off instead of retrying the rotate on every subsequent log line
        println("checkRotateLog: unable to rotate $FILE_LOG")
        MAX_LOG_SIZE - ROTATE_RETRY_SIZE.toLong()
    }
}

/**
 * custom console log writer with custom formatting
 * format: mm:ss [L] TT Tag: message
 * where:
 * - mm:ss is minutes:seconds
 * - L is severity level (D=Debug, V=Verbose, I=Info, W=Warn, E=Error)
 * - TT is 2-digit thread ID in hex (or "UI" for main thread)
 */
open class AppLogWriter : LogWriter() {

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        // date format: 03-03-15:39:21
        val dateStr = DateUtils.formatLogTime()

        // get thread identifier
        val threadId = getThreadId()

        val appTag = "$LOG_TAG_PREFIX$tag"

        // replace newlines with "<NL>"
        //val messageNoNewlines = message.replace("\n", "<NL>")

        // print to console
        logToConsole(dateStr, severity, threadId, appTag, message)
        if (throwable != null) {
            logToConsole(dateStr, severity, threadId, tag, throwable.stackTraceToString())
        }

        // file logging: INFO+, or everything if debug mode is on (verbose lines are only built
        // then anyway — see LoggerConfig.sharedConfig)
        val fileSeverity = if (LoggerConfig.isDebugMode) Severity.Verbose else Severity.Info
        val logToFile = severity >= fileSeverity
        if (logToFile) {
            val fileMessage = if (severity == Severity.Verbose) message.take(MAX_VERBOSE_FILE_CHARS) else message
            // queue for [startLogFileWriter] to batch; the channel is unlimited so this never blocks
            logQueue.trySend("$dateStr [${getSeverityDesc(severity)}] $threadId $appTag: $fileMessage\n")
        }
    }

    /**
     * get thread identifier - platform specific implementations should override this
     * @return 2-digit thread ID in hex (or "UI" for main thread)
     */
    protected open fun getThreadId(): String {
        // implement in platform-specific subclass
        return "??"
    }

    protected open fun logToConsole(timeStr: String, severity: Severity, threadId: String, tag: String, message: String) {
        // implement in platform-specific subclass
        println("$timeStr [${getSeverityDesc(severity)}] $threadId $tag: $message")
    }

    protected fun getSeverityDesc(severity: Severity): String {
        // get severity level letter
        return when (severity) {
            Severity.Verbose -> "V"
            Severity.Debug -> "D"
            Severity.Info -> "I"
            Severity.Warn -> "W"
            Severity.Error -> "E"
            Severity.Assert -> "A"
        }
    }

}
