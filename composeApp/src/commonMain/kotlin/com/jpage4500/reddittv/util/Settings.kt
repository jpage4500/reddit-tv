package com.jpage4500.reddittv.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.jpage4500.reddittv.data.AppPref
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * singleton settings manager for the app
 */
object SettingsManager {
    private val log = LoggerConfig.createLogger("SettingsManager")
    private val settings: Settings by lazy { createSettings() }

    // flow to emit preference changes, can be collected by view models to react to changes
    val prefChanges = MutableSharedFlow<AppPref>(extraBufferCapacity = 32)

    // one snapshot counter per pref, bumped whenever it changes — see [observePref]
    private val prefVersions: Map<AppPref, MutableState<Int>> =
        AppPref.entries.associateWith { mutableStateOf(0) }

    /**
     * subscribe the *calling composable* to [appPref], so changing the pref recomposes it.
     *
     * [getBoolAsState] and friends cover the usual shape, where a composable reads the pref itself.
     * This is for the other one: a plain function a composable calls to format a value — see
     * formatting a value by a display preference — where there's no state to hoist and no parameter
     * to thread through every call site. Reading the counter inside a composition records it against
     * the caller's recompose scope, however many plain calls deep the read is; outside a composition
     * the read does nothing.
     */
    fun observePref(appPref: AppPref): Int = prefVersions[appPref]?.value ?: 0

    fun getString(appPref: AppPref): String? {
        return settings.getString(appPref.key)
            ?: (appPref.defaultValue as? String)
    }

    // TODO: rename to getString but still need a version which can return null
    fun getStringOrEmpty(appPref: AppPref): String {
        return getString(appPref)
            ?: ""
    }

    fun setString(appPref: AppPref, value: String?) {
        // only update/emit if value changes
        val prevValue = getString(appPref)
        if (value == prevValue) return

        if (value == null) settings.remove(appPref.key)
        else settings.putString(appPref.key, value)
        prefVersions[appPref]?.let { it.value++ }
        prefChanges.tryEmit(appPref)
    }

    fun getBoolean(appPref: AppPref): Boolean {
        return getString(appPref)?.toBoolean()
            ?: (appPref.defaultValue as? Boolean)
            ?: false
    }

    fun setBoolean(appPref: AppPref, value: Boolean) {
        setString(appPref, value.toString())
    }

    fun getInt(appPref: AppPref): Int {
        return getString(appPref)?.toIntOrNull()
            ?: (appPref.defaultValue as? Int)
            ?: 0
    }

    fun setInt(appPref: AppPref, value: Int) {
        setString(appPref, value.toString())
    }

    fun getLong(appPref: AppPref): Long {
        return getString(appPref)?.toLongOrNull()
            ?: (appPref.defaultValue as? Long)
            ?: 0
    }

    fun setLong(appPref: AppPref, value: Long) {
        setString(appPref, value.toString())
    }

    fun getFloat(appPref: AppPref): Float {
        return getString(appPref)?.toFloatOrNull()
            ?: (appPref.defaultValue as? Float)
            ?: 0f
    }

    fun setFloat(appPref: AppPref, value: Float) {
        setString(appPref, value.toString())
    }

    /**
     * get enum value from settings
     * @param appPref the setting to get
     * @return the enum value or defaultValue
     */
    inline fun <reified T : Enum<T>> getEnum(appPref: AppPref): T {
        // try to get enum from string, fallback to defaultValue, then first enum
        return getString(appPref)
            ?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
            ?: (appPref.defaultValue as? T)
            ?: enumValues<T>().first()
    }

    /**
     * set enum value in settings
     * @param appPref the setting to set
     * @param value the enum value to set, or null to remove
     */
    fun <T : Enum<T>> setEnum(appPref: AppPref, value: T?) {
        setString(appPref, value?.name)
    }

    /**
     * an ordered list of enum values, persisted as a comma-separated list of names — for prefs that
     * hold a display order rather than a choice. names that no longer exist are dropped and any
     * constant the stored value doesn't mention is appended, so an order saved by an older build
     * still lists every section a newer one adds.
     */
    inline fun <reified T : Enum<T>> getEnumOrder(appPref: AppPref): List<T> {
        val stored = getString(appPref)
            ?.split(",")
            ?.mapNotNull { name -> runCatching { enumValueOf<T>(name.trim()) }.getOrNull() }
            ?.distinct()
            ?: emptyList()
        return stored + enumValues<T>().filter { it !in stored }
    }

    fun <T : Enum<T>> setEnumOrder(appPref: AppPref, values: List<T>) {
        setString(appPref, values.joinToString(",") { it.name })
    }

    /**
     * an unordered set of enum values, persisted the same way [getEnumOrder] persists a list — for
     * prefs that hold *which* constants are on rather than the order they're in. names that no longer
     * exist are dropped; unlike [getEnumOrder] nothing is added back, so a set saved by an older build
     * doesn't silently gain whatever a newer one declares.
     */
    inline fun <reified T : Enum<T>> getEnumSet(appPref: AppPref): Set<T> {
        return getString(appPref)
            ?.split(",")
            ?.mapNotNull { name -> runCatching { enumValueOf<T>(name.trim()) }.getOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    fun <T : Enum<T>> setEnumSet(appPref: AppPref, values: Set<T>) {
        // an empty set is stored as "" rather than clearing the pref — clearing it would fall back to
        // the pref's own default, so unchecking the last name would put the default straight back. ""
        // reads out as one unknown name, which [getEnumSet] drops, so it comes back as the empty set
        setString(appPref, values.joinToString(",") { it.name })
    }

    fun clearAll() {
        log.d("clearAll: clearing all settings")
        settings.clear()
        // update UI
        AppPref.entries.forEach {
            prefVersions[it]?.let { version -> version.value++ }
            prefChanges.tryEmit(it)
        }
    }

    fun getAll(): Map<String, String> {
        return settings.getAll()
    }

    fun setAll(settingsMap: Map<String, String>) {
        log.d("setAll: ${settingsMap.size} settings")
        settings.setAll(settingsMap)
        // update UI
        AppPref.entries.forEach {
            prefVersions[it]?.let { version -> version.value++ }
            prefChanges.tryEmit(it)
        }
    }

    @Composable
    fun <T> getPrefAsState(
        appPref: AppPref,
        initialValue: T,
        fetcher: (AppPref) -> T
    ): State<T> {
        return remember(appPref) {
            prefChanges
                .filter { it == appPref }
                .map { fetcher(appPref) }
                .distinctUntilChanged()
        }.collectAsState(initial = initialValue)
    }

    @Composable
    fun getAsState(appPref: AppPref): State<String?> =
        getPrefAsState(appPref, getString(appPref)) { getString(it) }

    @Composable
    fun getBoolAsState(appPref: AppPref): State<Boolean> =
        getPrefAsState(appPref, getBoolean(appPref)) { getBoolean(it) }

    @Composable
    fun getIntAsState(appPref: AppPref): State<Int> =
        getPrefAsState(appPref, getInt(appPref)) { getInt(it) }

    @Composable
    inline fun <reified T : Enum<T>> getEnumAsState(appPref: AppPref): State<T> =
        getPrefAsState(appPref, getEnum<T>(appPref)) { getEnum<T>(it) }

    @Composable
    inline fun <reified T : Enum<T>> getEnumOrderAsState(appPref: AppPref): State<List<T>> =
        getPrefAsState(appPref, getEnumOrder<T>(appPref)) { getEnumOrder<T>(it) }

    @Composable
    inline fun <reified T : Enum<T>> getEnumSetAsState(appPref: AppPref): State<Set<T>> =
        getPrefAsState(appPref, getEnumSet<T>(appPref)) { getEnumSet<T>(it) }

}

/**
 * cross-platform settings storage interface
 */
interface Settings {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun getAll(): Map<String, String>
    fun setAll(map: Map<String, String>)
    fun clear()
}

/**
 * expect/actual for platform-specific settings implementation
 */
expect fun createSettings(): Settings
