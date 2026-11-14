package com.jpage4500.reddittv.util

import kotlinx.coroutines.*
import java.net.NetworkInterface

actual fun createNetworkMonitor(): NetworkMonitor {
    return NetworkMonitor()
}

/**
 * Desktop implementation of network monitor.
 * Desktop typically uses wifi/ethernet, so we assume local mode unless the network is unavailable.
 */
actual class NetworkMonitor {
    private val log = LoggerConfig.createLogger("NetworkMonitor")

    // Scope tied to the lifecycle of this monitor to run background polling
    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    private var isCurrentlyConnected: Boolean = false

    actual fun startMonitoring(onNetworkChanged: () -> Unit) {
        // Prevent multiple jobs from running simultaneously
        stopMonitoring()

        // Set initial state
        isCurrentlyConnected = isNetworkAvailable()

        monitorJob = monitorScope.launch {
            while (isActive) {
                val newState = isNetworkAvailable()

                // If the state changes (e.g., laptop wakes up and Wi-Fi connects)
                if (newState != isCurrentlyConnected) {
                    log.v { "Network state changed: $isCurrentlyConnected -> $newState" }
                    isCurrentlyConnected = newState

                    // Trigger the callback.
                    // Note: This executes on Dispatchers.IO. If your callback updates the UI,
                    // ensure the caller switches to Dispatchers.Main.
                    onNetworkChanged()
                }

                // Poll every 3 seconds to catch sleep/wake transitions gracefully
                delay(3000)
            }
        }
    }

    actual fun stopMonitoring() {
        if (monitorJob?.isActive == true) {
            monitorJob?.cancel()
        }
        monitorJob = null
    }

    actual fun isWifiConnected(): Boolean {
        // Desktop is typically on wifi/ethernet, so assume true if the network is available
        return isNetworkAvailable()
    }

    actual fun isNetworkAvailable(): Boolean {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()

            interfaces?.toList()?.any { networkInterface ->
                val name = networkInterface.name.lowercase()

                // 1. Hardware must be on, not a local loopback, and not a virtual machine adapter
                networkInterface.isUp &&
                    !networkInterface.isLoopback &&
                    !networkInterface.isVirtual &&

                    // 2. Filter out known macOS phantom/tunnel interfaces
                    !name.startsWith("utun") && // VPNs, Tailscale, iCloud Private Relay
                    !name.startsWith("awdl") && // Apple Wireless Direct Link (AirDrop/AirPlay)
                    !name.startsWith("llw") &&  // Local Link Wireless

                    // 3. It MUST have a valid IP address assigned to it
                    networkInterface.inetAddresses.toList().any { address ->
                        !address.isLoopbackAddress && !address.isLinkLocalAddress
                    }
            } ?: false

        } catch (e: Exception) {
            log.e("Failed to check network interfaces: ${e.message}")
            false
        }
    }

    actual fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()

                // FILTER ADDED: Skip loopback, inactive, virtual, and point-to-point (VPN/utun) interfaces
                if (networkInterface.isLoopback ||
                    !networkInterface.isUp ||
                    networkInterface.isVirtual ||
                    networkInterface.isPointToPoint) {
                    continue
                }

                // Optional: If you want to be extremely strict about picking Wi-Fi/Ethernet,
                // you can also filter by standard interface names:
                // val name = networkInterface.name
                // if (!name.startsWith("en") && !name.startsWith("eth") && !name.startsWith("wlan")) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()

                    // Look for an IPv4 address
                    if (!address.isLoopbackAddress && address.hostAddress?.contains(":") == false) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}