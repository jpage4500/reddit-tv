package com.jpage4500.reddittv.util

import platform.Foundation.NSUserDefaults

private const val APP_GROUP_SUITE = "group.com.jpage4500.reddittv"
private const val MIGRATION_FLAG_KEY = "settings_migrated_to_group_v1"

private val iosSettingsLog = LoggerConfig.createLogger("Settings.ios")

/**
 * ios implementation using NSUserDefaults.
 *
 * Reads/writes to the App Group suite [APP_GROUP_SUITE] so the main app, Siri
 * Shortcuts extension, and Control Center widget extension all see the same
 * values. The app group must be enabled in entitlements for both targets; if
 * the capability is missing the suite silently ignores writes.
 */
private class IosSettings : Settings {
    private val userDefaults: NSUserDefaults = NSUserDefaults(suiteName = APP_GROUP_SUITE).also {
        migrateFromStandardDefaultsIfNeeded(it)
    }

    override fun getString(key: String): String? {
        return userDefaults.stringForKey(key)
    }

    override fun putString(key: String, value: String) {
        userDefaults.setObject(value, forKey = key)
    }

    override fun remove(key: String) {
        userDefaults.removeObjectForKey(key)
    }

    override fun clear() {
        // clear all keys from our app domain
        val domain = userDefaults.dictionaryRepresentation().keys
        domain.forEach { key ->
            userDefaults.removeObjectForKey(key as String)
        }
    }

    override fun getAll(): Map<String, String> {
        // return all key-value pairs as string map
        val dict = userDefaults.dictionaryRepresentation()
        val map = mutableMapOf<String, String>()
        for (entry in dict.entries) {
            val key = entry.key as? String ?: continue
            val value = entry.value?.toString() ?: ""
            map[key] = value
        }
        return map
    }

    override fun setAll(map: Map<String, String>) {
        map.forEach { (key, value) ->
            userDefaults.setObject(value, forKey = key)
        }
    }
}

/**
 * on first launch of a build with App Group entitlements, copy every key from
 * standardUserDefaults into the group suite so users don't get logged out.
 */
private fun migrateFromStandardDefaultsIfNeeded(group: NSUserDefaults) {
    if (group.boolForKey(MIGRATION_FLAG_KEY)) return
    val standard = NSUserDefaults.standardUserDefaults
    val dict = standard.dictionaryRepresentation()
    var copied = 0
    for (entry in dict.entries) {
        val key = entry.key as? String ?: continue
        if (key == MIGRATION_FLAG_KEY) continue
        if (group.objectForKey(key) != null) continue
        group.setObject(entry.value, forKey = key)
        copied++
    }
    group.setBool(true, forKey = MIGRATION_FLAG_KEY)
    iosSettingsLog.d("migrateFromStandardDefaultsIfNeeded: copied $copied keys into app group")
}

actual fun createSettings(): Settings {
    return IosSettings()
}
