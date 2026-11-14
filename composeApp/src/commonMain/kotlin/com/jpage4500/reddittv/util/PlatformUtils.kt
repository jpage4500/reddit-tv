package com.jpage4500.reddittv.util

import org.jetbrains.compose.resources.DrawableResource

enum class PlatformType {
    ANDROID,
    IOS,
    DESKTOP,
    WEB
}

// for DESKTOP only - returned in getPlatformName()
enum class DesktopOS {
    MAC,
    WINDOWS,
    LINUX,
}

/**
 * device RAM in bytes. free is what the OS reports as currently available to allocate;
 * total is the physical memory installed. both are 0 when the platform can't report them.
 */
data class MemoryInfo(val freeBytes: Long, val totalBytes: Long) {
    // ie: "2.1GB/7.6GB"
    override fun toString(): String =
        "${Utils.bytesToDisplayString(freeBytes)}/${Utils.bytesToDisplayString(totalBytes)}"
}

/**
 * cumulative CPU time burned by THIS process since it started, across all its threads, plus how many
 * cores that time could have been spread over. deliberately raw: a percentage only exists between
 * two samples, and [CpuSampler] owns that arithmetic so every platform reports the same thing.
 *
 * @param processCpuMs -1 when the platform can't report it
 */
data class CpuInfo(val processCpuMs: Long, val coreCount: Int)

/**
 * device identity where every field means the same thing on every platform.
 *
 * deliberately separate from [PlatformUtils.getPlatformName] / [PlatformUtils.getPlatformVersion] /
 * [PlatformUtils.getDeviceModel], which each return a different *kind* of value per platform — the
 * OS release on Android, the OS name on iOS, the CPU arch on desktop. those are fine for a log line
 * a human reads; analytics groups rows by these, and a column meaning three things can't be counted.
 * see docs/Analytics.md.
 */
data class DeviceInfo(
    // OS family name, never a number: "Android" / "iOS" / "iPadOS" / "macOS" / "Windows" / "Linux"
    val os: String,
    // the human version: "15" / "18.2" / "26.5.1"
    val osVersion: String,
    // Android's API level — the number a minSdk decision is actually made on. null elsewhere
    val osApi: Int? = null,
    // CPU architecture only: "arm64-v8a" / "arm64" / "aarch64"
    val arch: String,
    // the hardware: "SM-S942U1" / "iPhone17,2". null on desktop, where arch is all there is
    val deviceModel: String? = null,
    // how the app got here: "play" / "sideload" / "appstore" / "testflight" / "jdeploy"
    val installSource: String,
    // BCP-47 language tag: "en-US"
    val locale: String,
)

/**
 * derive a usable filename from a download URL by taking the last path segment,
 * stripping any query string. falls back to "download" for malformed URLs.
 */
fun fileNameFromUrl(url: String): String {
    val raw = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
    return raw.takeIf { it.isNotBlank() } ?: "download"
}

expect object PlatformUtils {
    /**
     * get the current platform (iOS, Android, Desktop)
     */
    fun getPlatform(): PlatformType

    /**
     * get the current platform name
     */
    fun getPlatformName(): String

    /**
     * get the current platform version
     */
    fun getPlatformVersion(): String

    /**
     * get the current device model
     */
    fun getDeviceModel(): String

    /**
     * OS family, version, arch, hardware, install channel and locale, each meaning the same thing
     * on every platform. see [DeviceInfo] for why this doesn't just reuse the three accessors above.
     */
    fun getDeviceInfo(): DeviceInfo

    /**
     * @return battery level (0-100) or -1 if not available/applicable
     */
    fun getBatteryLevel(): Int

    /**
     * free/total device memory (RAM)
     * Android: ActivityManager.MemoryInfo availMem/totalMem
     * iOS: mach host_statistics64 free+inactive pages / NSProcessInfo physicalMemory
     * desktop: OperatingSystemMXBean free/total physical memory
     * @return MemoryInfo, both values 0 if not available
     */
    fun getMemoryInfo(): MemoryInfo

    /**
     * cumulative CPU time used by this process, for [CpuSampler] to difference into a percentage.
     * Android: Process.getElapsedCpuTime()
     * iOS: getrusage(RUSAGE_SELF) user+system
     * desktop: OperatingSystemMXBean.processCpuTime
     * @return CpuInfo, processCpuMs -1 if not available
     */
    fun getCpuInfo(): CpuInfo

    fun isTablet(): Boolean

    /**
     * true on an Android TV / Google TV / Fire TV — where this app is the receiver rather than the
     * sender. the one APK installs on both, so this is a runtime check (see CLAUDE.md)
     */
    fun isTv(): Boolean

    /**
     * whether the device is awake enough for the network to be worth trying.
     *
     * Android only ever answers false: with the screen off the OS blocks a normal app's traffic, and
     * the resolver reports it as "no address associated with hostname" in ~15ms rather than as a
     * refusal — so an app launched from the IDE onto a sleeping phone, or a refresh timer firing at
     * 3am, watches every host fail at once. iOS and desktop don't run our code in that state, so
     * they answer true.
     */
    fun isDeviceAwake(): Boolean

    /**
     * true only when the Android app was installed via Google Play.
     * always false on iOS, Desktop, and sideloaded Android.
     */
    fun isPlayStoreInstall(): Boolean

    /**
     * true when this install keeps itself current from GitHub (UpdateManager): the sideloaded
     * android `full` flavor — phone or TV — and desktop. also the local `beta` flavor, so the update
     * UI can be tested there, although the APK on GitHub is `full`'s package and installs as a
     * second app beside it. false for a store install, iOS, and the `google` flavor however it got
     * there.
     */
    fun canUpdateItself(): Boolean

    /**
     * true only on a build compiled and run locally: the android `beta` flavor or any debuggable
     * android install, an iOS debug binary, and every desktop run — desktop ships through no store
     * at all. false in the android builds that do go out, the sideloaded `full` flavor included,
     * and in an App Store or TestFlight install.
     *
     * this is the gate for debug-only affordances that must never reach a shipped build, where
     * [com.jpage4500.reddittv.data.AppPref.DEBUG_MODE] isn't enough on its own — that pref is a
     * switch any user can find. see [com.jpage4500.reddittv.data.ProOverride].
     */
    fun isDebugBuild(): Boolean

    /**
     * open a URL in the default browser or map application
     * NOTE: also can open an external app if URL starts with "app://"
     * @return true if the URL/app was handled successfully, false on failure
     */
    fun openUrl(url: String): Boolean

    /**
     * send feedback email with attached log file
     * @param recipient email recipient address
     * @param subject email subject line
     * @param message email message body
     * @return true if email was successfully initiated, false if email support not available
     */
    fun sendFeedbackEmail(
        recipient: String,
        subject: String,
        message: String
    ): Boolean

    /**
     * cross-platform screen utility: keep screen on
     */
    fun setKeepScreenOn(enabled: Boolean)

    /**
     * cross-platform screen utility: full screen mode
     */
    fun setFullScreen(enabled: Boolean)

    /**
     * short haptic tick acknowledging a user action — call it right before sending a device command
     * so the tap is confirmed physically, independent of how long the hub takes to answer. always
     * respects the OS haptic setting, so a user who turned haptics off feels nothing.
     * desktop: no-op.
     */
    fun performHaptic()

    fun getPlatformIcon(): DrawableResource

    /**
     * true if the OS currently allows this app to install APKs.
     * Android: returns PackageManager.canRequestPackageInstalls(); always false elsewhere.
     */
    fun canInstallApk(): Boolean

    /**
     * deep-link the user to the OS settings where they can grant "install unknown apps".
     * no-op on platforms that don't support sideload installs.
     */
    fun requestInstallPermission()

    /**
     * download a file from url to the platform's app cache. filename is derived from the URL's last path segment.
     * emits progress via onProgress (downloaded, total). total may be -1 if the server doesn't report content-length.
     * @return absolute path of the downloaded file, or null on failure
     */
    suspend fun downloadFile(
        url: String,
        expectedSize: Long? = null,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): String?

    /**
     * fire the platform installer for the APK at the given absolute path.
     * @return true if the install intent was launched
     */
    fun installApk(absolutePath: String): Boolean

    /**
     * deep-link the user to this app's per-app system settings page.
     * no-op on platforms that don't expose per-app settings.
     */
    fun openAppSettings()
}
