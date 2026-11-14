package com.jpage4500.reddittv.util

import com.jpage4500.reddittv.data.Branding
import java.util.*

/**
 * desktop implementation using Properties file
 */
private class DesktopSettings : Settings {
    private val settingsFile = DesktopUtils.getHomeDir("settings.properties")
    private val properties = Properties()

    init {
        // load existing settings
        if (settingsFile.exists()) {
            settingsFile.inputStream().use { input ->
                properties.load(input)
            }
        }
    }

    override fun getString(key: String): String? {
        return properties.getProperty(key)
    }

    override fun putString(key: String, value: String) {
        properties.setProperty(key, value)
        save();
    }

    override fun remove(key: String) {
        properties.remove(key)
        save()
    }

    override fun clear() {
        properties.clear()
        save()
    }

    override fun getAll(): Map<String, String> {
        // return all key-value pairs as string map
        return properties.entries.associate { (k, v) ->
            k.toString() to v.toString()
        }
    }

    override fun setAll(map: Map<String, String>) {
        properties.putAll(map)
        save()
    }

    private fun save() {
        //writeExecutor.execute {
        settingsFile.outputStream().use { output ->
            properties.store(output, "${Branding.appName} Settings")
        }
        //}
    }
}

actual fun createSettings(): Settings {
    return DesktopSettings()
}
