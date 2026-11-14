package com.jpage4500.reddittv.util

/**
 * create platform-specific network monitor
 */
expect fun createNetworkMonitor(): NetworkMonitor

/**
 * monitor network connectivity changes and detect connection type
 */
expect class NetworkMonitor {
    /**
     * start monitoring network connectivity changes
     * @param onNetworkChanged callback when network connectivity changes
     */
    fun startMonitoring(onNetworkChanged: () -> Unit)

    /**
     * stop monitoring network connectivity changes
     */
    fun stopMonitoring()

    /**
     * check if device is connected via WiFi
     * @return true if connected via WiFi, false if cellular or other
     */
    fun isWifiConnected(): Boolean

    /**
     * check if device has any network connectivity
     * @return true if network is available
     */
    fun isNetworkAvailable(): Boolean

    fun getLocalIpAddress(): String?
}

