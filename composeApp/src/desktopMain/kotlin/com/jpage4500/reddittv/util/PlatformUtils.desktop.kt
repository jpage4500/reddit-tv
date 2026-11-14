package com.jpage4500.reddittv.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.jpage4500.reddittv.IS_RELEASE
import com.jpage4500.reddittv.manager.NetworkManager
import com.sun.management.OperatingSystemMXBean
import reddittv.composeapp.generated.resources.Res
import reddittv.composeapp.generated.resources.linux
import reddittv.composeapp.generated.resources.mac
import reddittv.composeapp.generated.resources.windows
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.DrawableResource
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.lang.management.ManagementFactory
import java.util.Locale
import reddittv.composeapp.generated.resources.*

private val log = LoggerConfig.createLogger("Platform.desktop")

// ES_CONTINUOUS | ES_DISPLAY_REQUIRED | ES_SYSTEM_REQUIRED, held by a thread that then waits on the
// app: the state lasts as long as the thread that set it
private val WINDOWS_KEEP_AWAKE = """
    ${'$'}api = Add-Type -PassThru -Name Awake -Namespace ShareToTv -MemberDefinition '[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint flags);'
    [void]${'$'}api::SetThreadExecutionState([uint32]2147483651)
    Wait-Process -Id ${ProcessHandle.current().pid()}
""".trimIndent()

actual object PlatformUtils {
    // the app's window, set by main() once it has one: what setFullScreen() fills the screen with
    var windowState: WindowState? = null
    // maximized or not, to go back to
    private var placementBeforeFullScreen = WindowPlacement.Floating
    // the command holding the display on, while something wants it on
    private var keepScreenOnProcess: Process? = null

    actual fun getPlatform(): PlatformType = PlatformType.DESKTOP

    actual fun getPlatformName(): String {
        return DesktopUtils.getDesktopOS().name
    }

    actual fun getPlatformVersion(): String {
        return System.getProperty("os.version") ?: "Unknown"
    }

    actual fun getDeviceModel(): String {
        return System.getProperty("os.arch") ?: "Unknown"
    }

    actual fun getDeviceInfo(): DeviceInfo = DeviceInfo(
        // the DesktopOS enum name ("MAC") is not what the other apps in the shared analytics table
        // send, and two labels for one OS makes `group by os` useless — map it here
        os = when (DesktopUtils.getDesktopOS()) {
            DesktopOS.MAC -> "macOS"
            DesktopOS.WINDOWS -> "Windows"
            DesktopOS.LINUX -> "Linux"
        },
        osVersion = System.getProperty("os.version") ?: "",
        // no API level on desktop
        osApi = null,
        arch = System.getProperty("os.arch") ?: "",
        // desktop has no hardware model worth a column; arch above is all there is
        deviceModel = null,
        // the only desktop distribution is the jDeploy installer; a dev run is tagged by is_dev
        installSource = "jdeploy",
        locale = Locale.getDefault().toLanguageTag(),
    )

    actual fun getBatteryLevel(): Int {
        return -1
    }

    actual fun getMemoryInfo(): MemoryInfo {
        return try {
            // physical memory is only exposed on the com.sun bean - the java.lang.management one
            // it extends has no such API
            val osBean = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean
                ?: return MemoryInfo(0, 0)
            MemoryInfo(osBean.freeMemorySize, osBean.totalMemorySize)
        } catch (e: Throwable) {
            // Throwable, not Exception: com.sun.management isn't in every JRE, so a trimmed
            // runtime fails here with NoClassDefFoundError
            log.e("getMemoryInfo: Exception: ${e.message}")
            MemoryInfo(0, 0)
        }
    }

    actual fun getCpuInfo(): CpuInfo {
        val cores = Runtime.getRuntime().availableProcessors()
        return try {
            // processCpuTime is ns and, like getMemoryInfo above, only exists on the com.sun bean
            val osBean = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean
                ?: return CpuInfo(-1L, cores)
            val cpuNs = osBean.processCpuTime
            // the bean returns -1 when the platform doesn't support it — keep that, don't divide it
            CpuInfo(if (cpuNs < 0) -1L else cpuNs / 1_000_000L, cores)
        } catch (e: Throwable) {
            // Throwable, not Exception: com.sun.management isn't in every JRE
            log.e("getCpuInfo: Exception: ${e.message}")
            CpuInfo(-1L, cores)
        }
    }

    actual fun isTablet(): Boolean = false

    // a sleeping laptop isn't running us either, and nothing here firewalls an app by screen state
    actual fun isDeviceAwake(): Boolean = true

    actual fun isTv(): Boolean = false

    actual fun isPlayStoreInstall(): Boolean = false

    actual fun canUpdateItself(): Boolean = true

    // anything but the jar jDeploy publishes: a run from Gradle or the IDE
    actual fun isDebugBuild(): Boolean = !IS_RELEASE

    actual fun openUrl(url: String): Boolean {
        try {
            if (!Desktop.isDesktopSupported()) return false
            val desktop = Desktop.getDesktop()
            // absolute local path (unix /path or windows C:\path) → hand to default app
            val asFile = java.io.File(url)
            if (asFile.isAbsolute && asFile.exists()) {
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(asFile)
                    return true
                }
                return false
            }
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(java.net.URI(url))
                return true
            }
            return false
        } catch (e: Exception) {
            log.e("openUrl: Exception: ${e.message}")
            return false
        }
    }

    actual fun sendFeedbackEmail(
        recipient: String,
        subject: String,
        message: String
    ): Boolean {
        return try {
            val logFile = DesktopUtils.getLogFile()
            message + "\n\nplease attach the log file: ${logFile?.absolutePath ?: "-"}"

            // try to open with system email client
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                try {
                    // encode subject and body for mailto URI
                    val encodedSubject = subject.replace(" ", "%20").replace("\n", "%0A")
                    val encodedBody = message.replace(" ", "%20").replace("\n", "%0A")
                    val mailtoUri = "mailto:$recipient?subject=$encodedSubject&body=$encodedBody"

                    Desktop.getDesktop().mail(java.net.URI(mailtoUri))
                    log.d("sendFeedbackEmail: opened email client")
                    true
                } catch (e: Exception) {
                    log.d("sendFeedbackEmail: could not open mail client: ${e.message}")
                    false
                }
            } else {
                ToastManager.showToast(Res.string.email_unavailable, recipient, success = false)
                false
            }
        } catch (e: Exception) {
            log.e("sendFeedbackEmail: Exception: ${e.message}")
            false
        }
    }

    actual fun setKeepScreenOn(enabled: Boolean) {
        synchronized(this) {
            if (enabled == (keepScreenOnProcess != null)) return
            log.d("setKeepScreenOn: $enabled")
            keepScreenOnProcess?.destroy()
            keepScreenOnProcess = null
            if (!enabled) return
            // there's no JVM call for this: each OS has a command that holds the display on for as
            // long as it runs
            val os = System.getProperty("os.name").orEmpty().lowercase()
            val command = when {
                // -w: gone with the app, however it exits
                os.contains("mac") -> listOf("caffeinate", "-d", "-w", ProcessHandle.current().pid().toString())
                os.contains("win") -> listOf("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", WINDOWS_KEEP_AWAKE)
                else -> listOf("systemd-inhibit", "--what=idle", "--who=Share to TV+", "--why=Playing video", "sleep", "infinity")
            }
            try {
                keepScreenOnProcess = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
            } catch (e: Exception) {
                log.e("setKeepScreenOn: Exception: ${e.message}")
            }
        }
    }

    actual fun setFullScreen(enabled: Boolean) {
        val state = windowState ?: return
        if (enabled == (state.placement == WindowPlacement.Fullscreen)) return
        log.d("setFullScreen: $enabled")
        if (enabled) {
            placementBeforeFullScreen = state.placement
            state.placement = WindowPlacement.Fullscreen
        } else {
            state.placement = placementBeforeFullScreen
        }
    }

    actual fun performHaptic() {
        // no haptics on desktop
    }

    actual fun getPlatformIcon(): DrawableResource {
        return when (DesktopUtils.getDesktopOS()) {
            DesktopOS.MAC -> Res.drawable.mac
            DesktopOS.WINDOWS -> Res.drawable.windows
            DesktopOS.LINUX -> Res.drawable.linux
        }
    }

    // desktop: coroutine timer in TimerAlarmManager is sufficient; no OS-level alarm needed

    actual fun canInstallApk(): Boolean = false

    actual fun requestInstallPermission() {}

    actual suspend fun downloadFile(
        url: String,
        expectedSize: Long?,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): String? {
        val tmp = System.getProperty("java.io.tmpdir") ?: "."
        val dir = File(tmp, "CastToTV/downloads")
        dir.mkdirs()
        val outFile = File(dir, fileNameFromUrl(url))
        if (outFile.exists()) outFile.delete()
        return try {
            val ok = FileOutputStream(outFile).use { out ->
                NetworkManager.downloadFile(url, expectedSize, onProgress) { buf, len ->
                    out.write(buf, 0, len)
                }
            }
            if (ok && outFile.length() > 0L) {
                log.d("downloadFile: saved ${outFile.length()} bytes to ${outFile.absolutePath}")
                outFile.absolutePath
            } else {
                outFile.delete()
                null
            }
        } catch (e: CancellationException) {
            outFile.delete()
            throw e
        } catch (e: Exception) {
            log.e("downloadFile: Exception: ${e.message}")
            outFile.delete()
            null
        }
    }

    actual fun installApk(absolutePath: String): Boolean = false

    actual fun openAppSettings() {
        // desktop: no per-app settings page
    }

}
