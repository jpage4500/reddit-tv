package com.jpage4500.reddittv.manager

import com.jpage4500.reddittv.APP_VERSION
import com.jpage4500.reddittv.data.AppPref
import com.jpage4500.reddittv.data.Branding
import com.jpage4500.reddittv.data.GitHubRelease
import com.jpage4500.reddittv.util.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UpdateManager {
    // only builds that update from GitHub (PlatformUtils.canUpdateItself) — a store install is the
    // store's to update. gates Check for Updates in the menus and the daily background check
    val isEnabled: Boolean by lazy { PlatformUtils.canUpdateItself() }

    // release repo (see Branding.githubRepo)
    private val githubRepo: String get() = Branding.githubRepo
    private const val ANDROID_TAG_SUFFIX = "-android"

    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L // once per day
    // coming back from the background: give the network a moment to reconnect
    private const val FOREGROUND_CHECK_DELAY_MS = 10_000L

    private val log = LoggerConfig.createLogger("UpdateManager")
    private val httpClient by lazy { createHttpClient(useCookies = false) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // reactive flag set by every checkForUpdates() call — UI observes this to surface
    // "Update Available" without re-running the check. starts from the version the last check
    // found, so the badge stays up across restarts until the next check (at most daily) runs
    private val _isUpdateAvailable = MutableStateFlow(
        isEnabled && isNewer(SettingsManager.getString(AppPref.UPDATE_VERSION))
    )
    val isUpdateAvailable: StateFlow<Boolean> = _isUpdateAvailable.asStateFlow()

    sealed class UpdateCheckResult {
        data class UpToDate(val currentVersion: String) : UpdateCheckResult()
        data class Available(
            val newVersion: String,
            val currentVersion: String,
            val notes: String,
            val releaseUrl: String,
            val apkUrl: String? = null,
            val apkSizeBytes: Long? = null,
        ) : UpdateCheckResult()

        data class StoreManaged(val store: Store, val storeUrl: String) : UpdateCheckResult()
        data class Error(val message: String) : UpdateCheckResult()
    }

    enum class Store { PLAY_STORE, APP_STORE }

    suspend fun checkForUpdates(): UpdateCheckResult {
        val result: UpdateCheckResult = when (PlatformUtils.getPlatform()) {
            PlatformType.IOS -> {
                log.d("checkForUpdates: iOS — store managed")
                UpdateCheckResult.StoreManaged(Store.APP_STORE, Branding.releasesUrl)
            }
            PlatformType.ANDROID -> {
                // github only: there is no store build
                checkAndroidGitHub()
            }
            PlatformType.DESKTOP -> checkDesktopGitHub()
            PlatformType.WEB -> UpdateCheckResult.Error("Web platform not supported")
        }
        // store-managed flows (iOS / Play Store) don't compare versions — leave the flag
        // untouched so a prior Available result isn't cleared
        if (result !is UpdateCheckResult.StoreManaged && result !is UpdateCheckResult.Error) {
            _isUpdateAvailable.value = result is UpdateCheckResult.Available
            SettingsManager.setString(AppPref.UPDATE_VERSION, (result as? UpdateCheckResult.Available)?.newVersion)
        }
        return result
    }

    fun isUpdateCheckNeeded(): Boolean {
        if (!isEnabled || !SettingsManager.getBoolean(AppPref.AUTO_UPDATE_CHECK)) return false
        val lastCheck = SettingsManager.getLong(AppPref.LAST_UPDATE_CHECK)
        val nowMs = DateUtils.nowMs()
        val elapsed = nowMs - lastCheck
        return (elapsed >= CHECK_INTERVAL_MS)
    }

    // background entry point called on app start; respects the once-per-day budget
    suspend fun checkForUpdatesIfDue() {
        if (!isUpdateCheckNeeded()) return
        log.d("checkForUpdatesIfDue: running background check")
        val result = checkForUpdates()
        if (result !is UpdateCheckResult.Error) {
            SettingsManager.setLong(AppPref.LAST_UPDATE_CHECK, DateUtils.nowMs())
        }
    }

    // a TV sits on this app for days without a restart: also check on the way back from the
    // background (screensaver, standby), still within the once-per-day budget
    fun onForeground() {
        if (!isUpdateCheckNeeded()) return
        scope.launch {
            delay(FOREGROUND_CHECK_DELAY_MS)
            checkForUpdatesIfDue()
        }
    }

    private suspend fun checkAndroidGitHub(): UpdateCheckResult {
        return try {
            // android releases are tagged with the "-android" suffix
            val latest = fetchReleases().firstOrNull {
                !it.draft && it.tagName.endsWith(ANDROID_TAG_SUFFIX)
            }
            if (latest == null) {
                log.d("checkAndroidGitHub: no android-tagged release found")
                return UpdateCheckResult.UpToDate(APP_VERSION)
            }
            val newVersion = latest.tagName.removeSuffix(ANDROID_TAG_SUFFIX)
            val apkAsset = latest.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            buildResult(newVersion, latest, apkAsset?.browserDownloadUrl, apkAsset?.size)
        } catch (e: Exception) {
            log.e("checkAndroidGitHub: Exception: ${e.message}")
            UpdateCheckResult.Error(e.message ?: "unknown error")
        }
    }

    private suspend fun checkDesktopGitHub(): UpdateCheckResult {
        return try {
            // desktop releases are tagged without a suffix. don't use GitHub's /releases/latest —
            // it can return the "-android" release (same commit, published moments later) whose
            // tag fails patch-version parsing and silently hides desktop updates
            // tags that are a version only: that also skips the fixed "android" release that
            // holds the newest APK at a stable link (build-android.sh)
            val latest = fetchReleases().firstOrNull {
                !it.draft && !it.tagName.endsWith(ANDROID_TAG_SUFFIX) && it.tagName.firstOrNull()?.isDigit() == true
            }
            if (latest == null) {
                log.d("checkDesktopGitHub: no desktop release found")
                return UpdateCheckResult.UpToDate(APP_VERSION)
            }
            buildResult(latest.tagName, latest)
        } catch (e: Exception) {
            log.e("checkDesktopGitHub: Exception: ${e.message}")
            UpdateCheckResult.Error(e.message ?: "unknown error")
        }
    }

    // fetches the repo's releases newest-first; throws on non-success so the caller
    // surfaces it as UpdateCheckResult.Error
    private suspend fun fetchReleases(): List<GitHubRelease> {
        val url = "https://api.github.com/repos/$githubRepo/releases?per_page=60"
        val response: HttpResponse = httpClient.get(url) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "${Branding.appName}/$APP_VERSION")
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}")
        }
        return response.body()
    }

    private fun buildResult(
        newVersion: String,
        release: GitHubRelease,
        apkUrl: String? = null,
        apkSizeBytes: Long? = null,
    ): UpdateCheckResult {
        log.d("buildResult: current=$APP_VERSION, remote=$newVersion, apkUrl=$apkUrl")
        return if (isNewer(newVersion)) {
            UpdateCheckResult.Available(
                newVersion = newVersion,
                currentVersion = APP_VERSION,
                notes = release.body.orEmpty(),
                releaseUrl = release.htmlUrl.orEmpty(),
                apkUrl = apkUrl,
                apkSizeBytes = apkSizeBytes,
            )
        } else {
            UpdateCheckResult.UpToDate(APP_VERSION)
        }
    }

    // true when [version] is newer than this build
    private fun isNewer(version: String?): Boolean {
        val current = parsePatchVersion(APP_VERSION) ?: return false
        val remote = version?.let { parsePatchVersion(it) } ?: return false
        return remote > current
    }

    // version format is "1.0.<commitCount>" — compare the patch component, tolerating any
    // non-numeric suffix (e.g. a "-android" release tag) so a mis-filed tag can't hide updates
    private fun parsePatchVersion(v: String): Int? {
        return v.substringAfterLast('.').takeWhile { it.isDigit() }.toIntOrNull()
    }
}
