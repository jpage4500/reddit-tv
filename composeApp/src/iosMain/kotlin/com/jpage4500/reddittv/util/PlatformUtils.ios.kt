package com.jpage4500.reddittv.util

import com.jpage4500.reddittv.manager.NetworkManager
import reddittv.composeapp.generated.resources.Res
import reddittv.composeapp.generated.resources.mac
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.DrawableResource
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMAuthorizationStatusDenied
import platform.CoreMotion.CMAuthorizationStatusRestricted
import platform.CoreMotion.CMMotionActivityManager
import platform.Foundation.*
import platform.MessageUI.MFMailComposeViewController
import platform.UIKit.*
import platform.darwin.HOST_VM_INFO64
import platform.darwin.KERN_SUCCESS
import platform.posix.RUSAGE_SELF
import platform.posix.getrusage
import platform.posix.rusage
import platform.posix.uname
import platform.posix.utsname
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.host_page_size
import platform.darwin.host_statistics64
import platform.darwin.mach_host_self
import platform.darwin.mach_msg_type_number_tVar
import platform.darwin.vm_size_tVar
import platform.darwin.vm_statistics64_data_t
import platform.UserNotifications.*
import reddittv.composeapp.generated.resources.*

private val log = LoggerConfig.createLogger("Platform.ios")

private const val COMMAND_ON = "ON"
private const val COMMAND_OFF = "OFF"
private const val COMMAND_TOGGLE = "TOGGLE"

actual object PlatformUtils {

    private var isFullScreenEnabled = false

    actual fun getPlatform(): PlatformType = PlatformType.IOS

    actual fun getPlatformName(): String {
        val device = UIDevice.currentDevice
        return device.systemName
    }

    actual fun getPlatformVersion(): String {
        val device = UIDevice.currentDevice
        return device.systemVersion
    }

    actual fun getDeviceModel(): String {
        val device = UIDevice.currentDevice
        return device.model
    }

    actual fun getDeviceInfo(): DeviceInfo {
        val device = UIDevice.currentDevice
        return DeviceInfo(
            // "iOS" / "iPadOS" — already a family name, unlike Android's accessor
            os = device.systemName,
            osVersion = device.systemVersion,
            // no API level on iOS
            osApi = null,
            // both declared targets are arm64 (see composeApp/build.gradle.kts)
            arch = "arm64",
            // "iPhone17,2" — UIDevice.model only ever says "iPhone" / "iPad"
            deviceModel = hardwareModel(),
            installSource = installSource(),
            locale = localeTag(),
        )
    }

    // uname's machine field, which is the marketing-free hardware id. on the simulator this is the
    // host arch rather than a device, which is fine — simulator runs are dev builds
    @OptIn(ExperimentalForeignApi::class)
    private fun hardwareModel(): String = memScoped {
        val info = alloc<utsname>()
        if (uname(info.ptr) != 0) return@memScoped ""
        info.machine.toKString()
    }

    // a TestFlight build carries a sandbox receipt where an App Store build carries a real one.
    // no receipt at all means a development install, which is dev anyway
    private fun installSource(): String {
        val receipt = NSBundle.mainBundle.appStoreReceiptURL?.lastPathComponent
        return if (receipt == "sandboxReceipt") "testflight" else "appstore"
    }

    // NSLocale hands back an ICU identifier ("en_US", sometimes with @keywords); the column wants a
    // BCP-47 tag, and every other platform sends one
    private fun localeTag(): String =
        NSLocale.currentLocale.localeIdentifier.substringBefore('@').replace('_', '-')

    actual fun getBatteryLevel(): Int {
        val device = UIDevice.currentDevice
        if (!device.batteryMonitoringEnabled) {
            device.batteryMonitoringEnabled = true
        }
        // convert to 0-100
        return (device.batteryLevel * 100).toInt()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getMemoryInfo(): MemoryInfo {
        val totalBytes = NSProcessInfo.processInfo.physicalMemory.toLong()
        var freeBytes = 0L
        memScoped {
            val host = mach_host_self()
            val stats = alloc<vm_statistics64_data_t>()
            val statsCount = alloc<mach_msg_type_number_tVar>()
            statsCount.value = (sizeOf<vm_statistics64_data_t>() / sizeOf<IntVar>()).convert()
            val pageSize = alloc<vm_size_tVar>()
            if (host_statistics64(host, HOST_VM_INFO64, stats.ptr.reinterpret(), statsCount.ptr) == KERN_SUCCESS &&
                host_page_size(host, pageSize.ptr) == KERN_SUCCESS
            ) {
                // 'free' the way the OS counts it: untouched pages plus inactive ones it can reclaim on demand
                val freePages = stats.free_count.toLong() + stats.inactive_count.toLong()
                freeBytes = freePages * pageSize.value.toLong()
            } else {
                log.d("getMemoryInfo: host_statistics64 failed")
            }
        }
        return MemoryInfo(freeBytes, totalBytes)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getCpuInfo(): CpuInfo {
        val cores = NSProcessInfo.processInfo.processorCount.toInt()
        var cpuMs = -1L
        memScoped {
            val usage = alloc<rusage>()
            if (getrusage(RUSAGE_SELF, usage.ptr) == 0) {
                // user + system, each split across whole seconds and a microsecond remainder
                val user = usage.ru_utime
                val sys = usage.ru_stime
                cpuMs = (user.tv_sec + sys.tv_sec) * 1000L +
                    (user.tv_usec + sys.tv_usec) / 1000L
            } else {
                log.d("getCpuInfo: getrusage failed")
            }
        }
        return CpuInfo(cpuMs, cores)
    }

    actual fun isTablet(): Boolean {
        return UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad
    }

    // iOS doesn't run this code with the device asleep — a backgrounded app is suspended, not
    // ticking away at a locked screen the way an Android process can be
    actual fun isDeviceAwake(): Boolean = true

    // tvOS gets its own SwiftUI receiver; this framework only ever runs on an iPhone or iPad
    actual fun isTv(): Boolean = false

    actual fun isPlayStoreInstall(): Boolean = false

    actual fun canUpdateItself(): Boolean = false

    // the framework Xcode builds for a local Debug run; a release archive — App Store or
    // TestFlight — answers false
    @OptIn(ExperimentalNativeApi::class)
    actual fun isDebugBuild(): Boolean = Platform.isDebugBinary

    // presenting a view controller and UIApplication.openURL are both main-thread only, so the answer
    // here is "handed to iOS", not "opened" — the one caller that acts on it (the log viewer) is on
    // the file-path branch, which still says no when there's nothing to present with
    actual fun openUrl(url: String): Boolean {
        // absolute file paths can't be opened via UIApplication.openURL (sandboxed);
        // hand off to the Swift-side QLPreviewController bridge
        if (url.startsWith("/")) {
            val viewer = globalFileViewer
            if (viewer != null) {
                onMainThread { viewer.viewFile(url) }
                return true
            } else {
                log.d("openUrl: globalFileViewer not set, cannot view $url")
                return false
            }
        }

        val nsUrl = NSURL.URLWithString(url) ?: return false
        onMainThread {
            val application = UIApplication.sharedApplication
            if (application.canOpenURL(nsUrl)) {
                application.openURL(
                    url = nsUrl,
                    options = emptyMap<Any?, Any>(),
                    completionHandler = null
                )
            } else {
                log.d("openUrl: nothing can open $url")
            }
        }
        return true
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun sendFeedbackEmail(
        recipient: String,
        subject: String,
        message: String
    ): Boolean {
        return try {
            // check if mail is available
            if (!MFMailComposeViewController.canSendMail()) {
                log.d("sendFeedbackEmail: mail not available on device")
                ToastManager.showToast(Res.string.mail_unavailable, success = false)
                return false
            }

            val mailController = MFMailComposeViewController()

            // set mail properties
            mailController.setToRecipients(listOf(recipient))
            mailController.setSubject(subject)
            mailController.setMessageBody(message, isHTML = false)

            // get log file and attach it (flush first - AppLogWriter batches its writes)
            flushLogFile()
            val logFilePath = getLogFilePath()
            if (logFilePath != null) {
                val fileData = NSData.dataWithContentsOfFile(logFilePath)
                if (fileData != null) {
                    mailController.addAttachmentData(
                        fileData,
                        mimeType = "text/plain",
                        fileName = FILE_LOG
                    )
                    log.d("sendFeedbackEmail: attached log file")
                }
            }

            // present mail composer from root view controller
            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (rootViewController != null) {
                rootViewController.presentViewController(mailController, animated = true, completion = null)
                log.d("sendFeedbackEmail: mail compose presented")
                true
            } else {
                log.d("sendFeedbackEmail: root view controller not available")
                false
            }
        } catch (e: Exception) {
            log.e("sendFeedbackEmail: Exception: ${e.message}")
            false
        }
    }

    actual fun setKeepScreenOn(enabled: Boolean) {
        log.v { "setKeepScreenOn: enabled=$enabled" }
        onMainThread { UIApplication.sharedApplication.idleTimerDisabled = enabled }
    }

    actual fun performHaptic() {
        // UIFeedbackGenerator is main-thread only, and honors the user's system haptic setting
        dispatch_async(dispatch_get_main_queue()) {
            val generator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
            generator.prepare()
            generator.impactOccurred()
        }
    }

    actual fun setFullScreen(enabled: Boolean) {
        // set before the hop: Swift reads it back through shouldHideStatusBar() on the way through
        // setNeedsStatusBarAppearanceUpdate(), and it's the answer to that which hides the bar
        isFullScreenEnabled = enabled
        onMainThread {
            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (rootViewController == null) {
                log.d("setFullScreen: rootViewController is null")
                return@onMainThread
            }
            log.v { "setFullScreen: enabled=$enabled" }
            rootViewController.setNeedsStatusBarAppearanceUpdate()
            // force layout update to recalculate safe area insets
            rootViewController.view.setNeedsLayout()
            rootViewController.view.layoutIfNeeded()
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }

    actual fun getPlatformIcon(): DrawableResource {
        return Res.drawable.mac
    }

    actual fun canInstallApk(): Boolean = false

    actual fun requestInstallPermission() {}

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun downloadFile(
        url: String,
        expectedSize: Long?,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): String? {
        val cachesDir = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: return null
        val downloadsDir = "$cachesDir/downloads"
        NSFileManager.defaultManager.createDirectoryAtPath(
            downloadsDir, withIntermediateDirectories = true, attributes = null, error = null
        )
        val destPath = "$downloadsDir/${fileNameFromUrl(url)}"
        NSFileManager.defaultManager.removeItemAtPath(destPath, error = null)
        NSFileManager.defaultManager.createFileAtPath(destPath, contents = null, attributes = null)
        val handle = NSFileHandle.fileHandleForWritingAtPath(destPath) ?: return null
        return try {
            val ok = NetworkManager.downloadFile(url, expectedSize, onProgress) { buf, len ->
                buf.usePinned { pinned ->
                    val data = NSData.dataWithBytes(pinned.addressOf(0), len.convert())
                    handle.writeData(data)
                }
            }
            handle.closeFile()
            if (ok) {
                log.d("downloadFile: saved to $destPath")
                destPath
            } else {
                NSFileManager.defaultManager.removeItemAtPath(destPath, error = null)
                null
            }
        } catch (e: CancellationException) {
            handle.closeFile()
            NSFileManager.defaultManager.removeItemAtPath(destPath, error = null)
            throw e
        } catch (e: Exception) {
            handle.closeFile()
            NSFileManager.defaultManager.removeItemAtPath(destPath, error = null)
            log.e("downloadFile: Exception: ${e.message}")
            null
        }
    }

    actual fun installApk(absolutePath: String): Boolean = false

    actual fun openAppSettings() {
        log.d("openAppSettings")
        val url = NSURL(string = UIApplicationOpenSettingsURLString)
        onMainThread {
            val app = UIApplication.sharedApplication
            if (app.canOpenURL(url)) {
                app.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
            }
        }
    }

    // top-level function for Swift interop
    fun shouldHideStatusBar(): Boolean {
        return isFullScreenEnabled
    }

    /**
     * get the log file path from iOS file system
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun getLogFilePath(): String? {
        return try {
            val fileManager = NSFileManager.defaultManager
            val documentDir = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true
            ).first() as? String ?: return null

            val logFilePath = "$documentDir/$PATH_LOGS/$FILE_LOG"
            if (fileManager.fileExistsAtPath(logFilePath)) {
                logFilePath
            } else {
                log.d("getLogFilePath: log file not found at $logFilePath")
                null
            }
        } catch (e: Exception) {
            log.e("getLogFilePath: Exception: ${e.message}")
            null
        }
    }

}