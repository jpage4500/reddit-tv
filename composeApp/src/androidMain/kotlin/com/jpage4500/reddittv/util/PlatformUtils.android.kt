package com.jpage4500.reddittv.util

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.jpage4500.reddittv.manager.NetworkManager
import reddittv.composeapp.generated.resources.Res
import reddittv.composeapp.generated.resources.device_android
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.DrawableResource
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.Locale

private val log = LoggerConfig.createLogger("Platform.android")

// downscale target for app launcher icons shown in the app picker (px)
private const val ICON_SIZE_PX = 128

actual object PlatformUtils {

    private var screenActivityRef: WeakReference<Activity>? = null
    // internal rather than private: StoreBilling needs it to show the purchase sheet
    internal val screenActivity get() = screenActivityRef?.get()

    /**
     * set the activity reference used for screen utilities
     */
    fun setScreenActivity(act: Activity?) {
        // logged with its identity: every screen utility is a no-op without this, so a lost or
        // swapped reference is the first thing to rule out when the screen won't stay on
        log.d("setScreenActivity: ${act?.let { "${it::class.simpleName}@${it.hashCode()}" } ?: "cleared"}")
        screenActivityRef = if (act == null) null else WeakReference(act)
    }

    /**
     * drop the reference, but only if [act] is still the one holding it. a second instance can be
     * created before the first is destroyed, and clearing it then would leave the *live* activity's
     * window unreachable — every later [setKeepScreenOn] a silent no-op until the next onCreate
     */
    fun clearScreenActivity(act: Activity) {
        if (screenActivity !== act) {
            log.d("clearScreenActivity: a newer activity holds it - keeping")
            return
        }
        setScreenActivity(null)
    }

    actual fun getPlatform(): PlatformType = PlatformType.ANDROID

    actual fun getPlatformName(): String {
        return Build.VERSION.RELEASE
    }

    actual fun getPlatformVersion(): String {
        return Build.VERSION.SDK_INT.toString()
    }

    actual fun getDeviceModel(): String {
        return Build.MODEL
    }

    actual fun getDeviceInfo(): DeviceInfo = DeviceInfo(
        // the family name, not Build.VERSION.RELEASE — that's the number, and it goes in osVersion
        os = "Android",
        osVersion = Build.VERSION.RELEASE ?: "",
        osApi = Build.VERSION.SDK_INT,
        // the ABI this process actually runs, first in the device's preference order
        arch = Build.SUPPORTED_ABIS?.firstOrNull() ?: "",
        deviceModel = Build.MODEL,
        installSource = if (isPlayStoreInstall()) "play" else "sideload",
        locale = Locale.getDefault().toLanguageTag(),
    )

    actual fun getBatteryLevel(): Int {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = AndroidContextProvider.context.registerReceiver(null, intentFilter)

            if (batteryStatus != null) {
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                // return 0-100
                (level.toFloat() / scale.toFloat() * 100).toInt()
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }

    actual fun getMemoryInfo(): MemoryInfo {
        return try {
            val activityManager = AndroidContextProvider.context
                .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(info)
            MemoryInfo(info.availMem, info.totalMem)
        } catch (e: Exception) {
            log.e("getMemoryInfo: Exception: ${e.message}")
            MemoryInfo(0, 0)
        }
    }

    actual fun getCpuInfo(): CpuInfo {
        return try {
            // user+system time summed across this process's threads. a single
            // clock_gettime(CLOCK_PROCESS_CPUTIME_ID) under the hood — no /proc read to parse, no
            // permission, and unaffected by the Android 8 restriction on other processes' /proc
            CpuInfo(Process.getElapsedCpuTime(), Runtime.getRuntime().availableProcessors())
        } catch (e: Exception) {
            log.e("getCpuInfo: Exception: ${e.message}")
            CpuInfo(-1L, 0)
        }
    }

    actual fun isTablet(): Boolean {
        return AndroidContextProvider.context.resources.configuration.smallestScreenWidthDp >= 600
    }

    // leanback covers Android TV and Google TV; Fire OS declares its own feature, and its UI mode
    // is television on every stick
    private val isTvDevice: Boolean by lazy {
        val context = AndroidContextProvider.context
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val pm = context.packageManager
        uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature("amazon.hardware.fire_tv")
    }

    actual fun isTv(): Boolean = isTvDevice

    actual fun isDeviceAwake(): Boolean {
        val pm = AndroidContextProvider.context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        // isInteractive() is the screen; isDeviceIdleMode() is the Doze that follows it being off.
        // either one being wrong is the OS dropping our packets, not a network to retry against
        return pm.isInteractive && !pm.isDeviceIdleMode
    }

    actual fun isPlayStoreInstall(): Boolean {
        return try {
            val context = AndroidContextProvider.context
            val pm = context.packageManager
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
            installer == "com.android.vending" || installer == "com.google.android.feedback"
        } catch (e: Exception) {
            log.e("isPlayStoreInstall: Exception: ${e.message}")
            false
        }
    }

    // `beta` too, so a local build shows the update UI to test and screenshot — its Update installs
    // the `full` app alongside it, since that's the package the GitHub APK is
    actual fun canUpdateItself(): Boolean {
        val packageName = AndroidContextProvider.context.packageName
        return (packageName.endsWith(".full") || packageName.endsWith(".beta")) && !isPlayStoreInstall()
    }

    // two ways to be a local build, because the usual one isn't debuggable: build-android.sh
    // installs assembleBetaRelease, so the `beta` flavor — dev builds, and the only flavor that
    // never goes out to anyone — is recognised by its applicationId suffix. FLAG_DEBUGGABLE covers
    // the rest (anything run from the IDE). both read the install that's actually running, rather
    // than BuildConfig: composeApp is a library, so its BuildConfig follows the variant the library
    // was built as, not the apk's
    actual fun isDebugBuild(): Boolean {
        val context = AndroidContextProvider.context
        if (context.packageName.endsWith(".beta")) return true
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    // true if the given permission is declared in the merged manifest (used to detect the
    // sideload flavor at runtime without depending on the androidApp BuildConfig)
    private fun isPermissionDeclared(permission: String): Boolean {
        return try {
            val context = AndroidContextProvider.context
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            info.requestedPermissions?.contains(permission) == true
        } catch (e: Exception) {
            log.e("isPermissionDeclared: Exception: ${e.message}")
            false
        }
    }

    actual fun openUrl(url: String): Boolean {
        try {
            val context = AndroidContextProvider.context
            // callers pass one of: a content:// URI (MediaStore, Q+), an absolute file path
            // (pre-Q public storage), or any other URL (http, custom scheme, etc.). Raw file://
            // intents throw FileUriExposedException on API 24+, so wrap file paths via FileProvider.
            val isFile = url.startsWith("/")
            val target: Uri = if (isFile) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(url)
                )
            } else {
                Uri.parse(url)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                // FileProvider derives MIME from the file extension; extensions with no registered
                // type (e.g. .log) resolve to null, leaving no activity able to handle ACTION_VIEW.
                // fall back to text/plain for local files so a text viewer can open them.
                val mime = context.contentResolver.getType(target) ?: if (isFile) "text/plain" else null
                if (mime != null) setDataAndType(target, mime) else data = target
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            return true
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
            val context = AndroidContextProvider.context
            // get log file from FileLogWriter (flush first - AppLogWriter batches its writes)
            flushLogFile()
            val filesDir = File(context.filesDir, PATH_LOGS)
            val logFile = File(filesDir, FILE_LOG)

            // get URI for log file using FileProvider
            val logFileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                logFile
            )

            // create email intent
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra(Intent.EXTRA_STREAM, logFileUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)
            log.d("sendFeedbackEmail: email intent started")
            true
        } catch (e: Exception) {
            log.e("sendFeedbackEmail: Exception: ${e.message}")
            false
        }
    }

    actual fun setKeepScreenOn(enabled: Boolean) {
        val activity = screenActivity
        if (activity == null) {
            // the flag belongs to a window, and a wake alarm can run apply() in a process that has
            // none. nothing holds the screen until the activity resumes and asks again
            log.d("setKeepScreenOn: $enabled - no activity, ignored")
            return
        }
        activity.runOnUiThread {
            val window = activity.window
            if (window == null) {
                log.d("setKeepScreenOn: $enabled - no window, ignored")
                return@runOnUiThread
            }
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            // read the flag back rather than trusting the call: ScreenOnManager's own "keepOn:true"
            // only says what was asked for
            log.d("setKeepScreenOn: $enabled (flag:${isKeepScreenOnSet()})")
        }
    }

    /** whether the flag is on the activity's window right now — the read half of [setKeepScreenOn] */
    internal fun isKeepScreenOnSet(): Boolean {
        val window = screenActivity?.window ?: return false
        return (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
    }

    actual fun performHaptic() {
        val activity = screenActivity ?: return
        activity.runOnUiThread {
            // performHapticFeedback on a View needs no VIBRATE permission and honors the OS haptic
            // setting (unlike Vibrator). CONTEXT_CLICK is the short confirm-style tick, API 23+
            activity.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    actual fun setFullScreen(enabled: Boolean) {
        val activity = screenActivity ?: return
        activity.runOnUiThread {
            val window = activity.window ?: return@runOnUiThread

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // use modern API for Android 11+
                val controller = window.insetsController
                if (enabled) {
                    controller?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                    controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                }
            } else {
                // fallback for older Android versions
                @Suppress("DEPRECATION")
                if (enabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
            }
        }
    }

    actual fun getPlatformIcon(): DrawableResource {
        return Res.drawable.device_android
    }

    actual fun canInstallApk(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AndroidContextProvider.context.packageManager.canRequestPackageInstalls()
            } else {
                true
            }
        } catch (e: Exception) {
            log.e("canInstallApk: Exception: ${e.message}")
            false
        }
    }

    actual fun requestInstallPermission() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val context = AndroidContextProvider.context
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            log.e("requestInstallPermission: Exception: ${e.message}")
        }
    }

    actual suspend fun downloadFile(
        url: String,
        expectedSize: Long?,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): String? {
        val dir = File(AndroidContextProvider.context.cacheDir, "downloads")
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
                log.e("downloadFile: download failed or empty file")
                outFile.delete()
                null
            }
        } catch (e: CancellationException) {
            log.d("downloadFile: cancelled")
            outFile.delete()
            throw e
        } catch (e: Exception) {
            log.e("downloadFile: Exception: ${e.message}")
            outFile.delete()
            null
        }
    }

    actual fun openAppSettings() {
        try {
            val context = AndroidContextProvider.context
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            log.e("openAppSettings: Exception: ${e.message}")
        }
    }

    actual fun installApk(absolutePath: String): Boolean {
        return try {
            val context = AndroidContextProvider.context
            val file = File(absolutePath)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            log.d("installApk: started installer for $absolutePath")
            true
        } catch (e: Exception) {
            log.e("installApk: Exception: ${e.message}")
            false
        }
    }

}
