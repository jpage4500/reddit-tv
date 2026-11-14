package com.jpage4500.reddittv.util

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.jpage4500.reddittv.data.AppPref
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlin.concurrent.Volatile
import co.touchlab.kermit.LoggerConfig as KermitLoggerConfig

/**
 * logger configuration for the application
 * manages logger initialization with platform-specific writers
 */
object LoggerConfig {
    var logWriter: LogWriter? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * cached [AppPref.DEBUG_MODE] — read this instead of the setting itself from anything that
     * runs per log line; a settings lookup there is a bridged NSUserDefaults call on ios
     */
    @Volatile
    var isDebugMode = false
        private set

    // single shared writer that delegates to whatever logWriter is currently set
    private val delegatingWriter = object : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            // a provider's login never reaches the console, logcat or the log file
            logWriter?.log(severity, LogRedactor.redact(message), tag, throwable)
        }
    }

    /**
     * one config shared by every logger. minSeverity is the only gate kermit applies *before*
     * evaluating a `log.v { }` lambda — a writer's own isLoggable() and everything in
     * [AppLogWriter.log] run once the message string is already built — so dropping verbose
     * lines here is what makes them free. debug stays enabled either way, leaving console
     * output unchanged; [AppLogWriter] decides separately what reaches the log file
     */
    private val sharedConfig = object : KermitLoggerConfig {
        override val logWriterList = listOf(delegatingWriter)
        override val minSeverity: Severity
            get() = if (isDebugMode) Severity.Verbose else Severity.Debug
    }

    /**
     * initialize logger
     * call this from platform-specific code (e.g., MainActivity on Android)
     */
    fun initialize(logWriter: LogWriter? = null) {
        this.logWriter = logWriter
        isDebugMode = SettingsManager.getBoolean(AppPref.DEBUG_MODE)
        // a bulk replace (setAll/clearAll) emits every pref, so a restore is covered too
        scope.launch {
            SettingsManager.prefChanges.filter { it == AppPref.DEBUG_MODE }.collect {
                isDebugMode = SettingsManager.getBoolean(AppPref.DEBUG_MODE)
            }
        }
        startLogFileWriter()
    }

    fun createLogger(tag: String): Logger {
        // one shared config, so a DEBUG_MODE change applies to every logger at once
        return Logger(config = sharedConfig, tag = tag)
    }

}
