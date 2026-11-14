package com.jpage4500.reddittv.util

import kotlinx.cinterop.*
import platform.Network.*
import platform.darwin.*
import platform.posix.AF_INET
import platform.posix.INET_ADDRSTRLEN
import platform.posix.sa_family_t
import platform.posix.sockaddr_in

actual fun createNetworkMonitor(): NetworkMonitor {
    return NetworkMonitor()
}

/**
 * ios implementation of network monitor
 * uses network framework to monitor connectivity and connection type changes
 */
@OptIn(ExperimentalForeignApi::class)
actual class NetworkMonitor {
    private val log = LoggerConfig.createLogger("NetworkMonitor")

    private var pathMonitor: nw_path_monitor_t = null
    private var monitorQueue: dispatch_queue_t = null
    private var currentPath: nw_path_t = null
    private var isFirstUpdate = true

    private var lastWasOnline: Boolean? = null
    private var lastWasWifi: Boolean? = null

    actual fun startMonitoring(onNetworkChanged: () -> Unit) {
        stopMonitoring()

        isFirstUpdate = true
        // Reset states on start
        lastWasOnline = null
        lastWasWifi = null

        // create path monitor
        pathMonitor = nw_path_monitor_create()

        if (pathMonitor == null) {
            log.e { "startMonitoring: failed to create path monitor" }
            return
        }

        // create dispatch queue for monitoring
        monitorQueue = dispatch_queue_create("com.jpage4500.reddittv.NetworkMonitor", null)

        if (monitorQueue == null) {
            log.e { "startMonitoring: failed to create dispatch queue" }
            pathMonitor?.let { nw_path_monitor_cancel(it) }
            pathMonitor = null
            return
        }

        // nw_path_monitor_start is async. block here until the framework's initial
        // path evaluation lands so callers don't see currentPath=null and incorrectly
        // conclude the device is offline.
        val initReady = dispatch_semaphore_create(0)

        // set update handler to capture path changes
        nw_path_monitor_set_update_handler(pathMonitor) { path ->
            currentPath = path

            if (isFirstUpdate) {
                // Swallow Apple's immediate initial callback
                isFirstUpdate = false
                dispatch_semaphore_signal(initReady)
                return@nw_path_monitor_set_update_handler
            }

            // 2. Calculate the current state
            val isOnline = nw_path_get_status(path) == nw_path_status_satisfied
            val isWifi = checkIsWifi(path)

            // 3. Only notify if something meaningful changed
            if (isOnline != lastWasOnline || isWifi != lastWasWifi) {
                lastWasOnline = isOnline
                lastWasWifi = isWifi
                onNetworkChanged()
            }
        }

        // set queue for the monitor
        nw_path_monitor_set_queue(pathMonitor, monitorQueue)

        // start monitoring
        nw_path_monitor_start(pathMonitor)

        // wait for the first path callback (typically ~10-50ms). cap at 500ms so a
        // misbehaving framework can't hang the calling thread.
        val timeout = dispatch_time(DISPATCH_TIME_NOW, 500_000_000L)
        if (dispatch_semaphore_wait(initReady, timeout) != 0L) {
            log.w { "startMonitoring: timed out waiting for initial path evaluation" }
        }
    }

    actual fun stopMonitoring() {
        pathMonitor?.let {
            nw_path_monitor_cancel(it)
            pathMonitor = null
        }
        currentPath = null
        monitorQueue = null
    }

    actual fun isWifiConnected(): Boolean {
        val result = currentPath?.let { checkIsWifi(it) } ?: false
        return result
    }

    private fun checkIsWifi(path: nw_path_t): Boolean {
        if (nw_path_get_status(path) != nw_path_status_satisfied) return false
        // distinguish Wi-Fi from cellular at the interface level so callers
        // (callers deciding whether to attempt a request) can avoid
        // 8s+ local-LAN timeouts while the device is on cellular.
        return nw_path_uses_interface_type(path, nw_interface_type_wifi)
    }

    actual fun isNetworkAvailable(): Boolean {
        val isAvailable = currentPath?.let {
            nw_path_get_status(it) == nw_path_status_satisfied
        } ?: false
        return isAvailable
    }

    actual fun getLocalIpAddress(): String? {
        var ipAddress: String? = null

        try {
            memScoped {
                val ifap = allocPointerTo<ifaddrs>()
                if (getifaddrs(ifap.ptr) == 0) {
                    var ptr = ifap.value
                    while (ptr != null) {
                        val interfaceData = ptr.pointed
                        val addr = interfaceData.ifa_addr?.pointed

                        if (addr?.sa_family == AF_INET.convert<sa_family_t>()) {
                            val name = interfaceData.ifa_name?.toKString()

                            // filter specifically for Wi-Fi (en0)
                            // (could also add "en1" to support wired ethernet adapters on iPad)
                            if (name == "en0") {
                                // use inet_ntop with a caller-owned buffer rather than
                                // inet_ntoa(sin_addr.readValue()) — passing the struct by value
                                // tripped a Kotlin/Native interop error on device
                                val sockaddr = addr.reinterpret<sockaddr_in>()
                                val buffer = ByteArray(INET_ADDRSTRLEN)
                                buffer.usePinned { pinned ->
                                    val res = inet_ntop(
                                        AF_INET,
                                        sockaddr.sin_addr.ptr,
                                        pinned.addressOf(0),
                                        INET_ADDRSTRLEN.convert()
                                    )
                                    if (res != null) ipAddress = buffer.toKString()
                                }
                                break
                            }
                        }
                        ptr = interfaceData.ifa_next
                    }
                    freeifaddrs(ifap.value)
                }
            }
        } catch (e: Exception) {
            // contain here so checkCloudMode's AUTO branch isn't blanket-failed
            log.w { "getLocalIpAddress: exception: ${e.message}" }
        }
        return ipAddress
    }
}

