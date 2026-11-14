package com.jpage4500.reddittv.util

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.*
import android.net.NetworkRequest
import androidx.annotation.RequiresPermission
import java.net.NetworkInterface

actual fun createNetworkMonitor(): NetworkMonitor {
    return NetworkMonitor(AndroidContextProvider.context)
}

/**
 * android implementation of network monitor
 */
actual class NetworkMonitor(private val context: Context) {
    private val log = LoggerConfig.createLogger("NetworkMonitor")

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var onNetworkChangedCallback: (() -> Unit)? = null
    private var lastStatus: NetworkStatus? = null

    private data class NetworkStatus(
        val online: Boolean,
        val transport: String,
        val metered: Boolean
    )

    private fun toStatus(capabilities: NetworkCapabilities): NetworkStatus {
        val transport = when {
            capabilities.hasTransport(TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        val online = capabilities.hasCapability(NET_CAPABILITY_INTERNET)
        val metered = !capabilities.hasCapability(NET_CAPABILITY_NOT_METERED)
        return NetworkStatus(online, transport, metered)
    }

    private fun updateStatus(reason: String, status: NetworkStatus?) {
        val newStatus = status ?: NetworkStatus(false, "none", true)
        val previous = lastStatus
        if (previous == newStatus) return
        log.d { "$reason: status changed $previous -> $newStatus" }
        lastStatus = newStatus
        onNetworkChangedCallback?.invoke()
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    actual fun startMonitoring(onNetworkChanged: () -> Unit) {
        stopMonitoring()
        onNetworkChangedCallback = onNetworkChanged

        // Pre-populate lastStatus to prevent Android's immediate initial callback
        // from looking like a state change and triggering a redundant fetch
        val activeNetwork = connectivityManager.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        lastStatus = if (caps != null && caps.hasCapability(NET_CAPABILITY_VALIDATED)) {
            toStatus(caps)
        } else {
            // Default offline state (matches the fallback in updateStatus)
            NetworkStatus(online = false, transport = "none", metered = true)
        }

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
            override fun onAvailable(network: Network) {
                val active = connectivityManager.activeNetwork
                val caps = connectivityManager.getNetworkCapabilities(network)
                if (network != active || caps == null || !caps.hasCapability(NET_CAPABILITY_VALIDATED)) {
                    //log.v("onAvailable: ignoring non-active or unvalidated network: $network")
                    return
                }
                updateStatus("onAvailable", toStatus(caps))
            }

            override fun onLost(network: Network) {
                updateStatus("onLost", null)
            }

            @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val active = connectivityManager.activeNetwork
                if (network != active || !networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED)) {
                    //log.v("onCapabilitiesChanged: ignoring non-active or unvalidated network: $network != $active")
                    return
                }
                updateStatus("onCapabilitiesChanged", toStatus(networkCapabilities))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }

    actual fun stopMonitoring() {
        networkCallback?.let {
            //log.v { "stopMonitoring: unregistering network callback" }
            connectivityManager.unregisterNetworkCallback(it)
            networkCallback = null
        }
        onNetworkChangedCallback = null
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    actual fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(TRANSPORT_WIFI) || capabilities.hasTransport(TRANSPORT_ETHERNET)
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    actual fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NET_CAPABILITY_INTERNET)
    }

    actual fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()

                // Skip loopback, inactive, virtual, and point-to-point (VPN/tun0) interfaces
                if (networkInterface.isLoopback ||
                    !networkInterface.isUp ||
                    networkInterface.isVirtual ||
                    networkInterface.isPointToPoint
                ) {
                    continue
                }

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
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
