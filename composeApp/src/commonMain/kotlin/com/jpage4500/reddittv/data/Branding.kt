package com.jpage4500.reddittv.data

import androidx.compose.ui.graphics.Color
import com.jpage4500.reddittv.ui.theme.Colors
import reddittv.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

/**
 * brand colors, copy and images for the app.
 *
 * everything user-facing that names or styles the app is routed through here so a rebrand is a
 * single-file change. keep [appName] in sync with `brandAppName` in composeApp/build.gradle.kts
 * (desktop installer + window title) and androidApp/build.gradle.kts (launcher label).
 */
object Branding {
    // user-facing app name — window title, macOS menu bar, tray tooltip, menu header
    val appName: String = "RedditTV"

    // github repo hosting desktop installers + sideloaded android APKs (see UpdateManager)
    val githubRepo: String = "jpage4500/reddit-tv"

    // the public repo's page
    val githubUrl: String get() = "https://github.com/$githubRepo"

    // every release (Android phone/TV, desktop)
    val releasesUrl: String get() = "https://github.com/$githubRepo/releases"

    // the newest release's page
    val latestReleaseUrl: String get() = "$releasesUrl/latest"

    // the newest Android APK, at a link that never changes — build-android.sh replaces the one
    // asset on the fixed "android" release every time. for the Downloader app on a TV, and QR codes
    val apkUrl: String get() = "https://github.com/$githubRepo/releases/download/android/RedditTV.apk"

    // directory under the user's home where desktop settings/logs live
    val desktopHomeDir: String = ".reddit-tv"

    // the mark with no background (logo-foreground.svg)
    val logo: DrawableResource = Res.drawable.logo_foreground

    // the same mark cropped to its artwork
    val mark: DrawableResource = Res.drawable.logo_mark

    // circular-masked brand logo — desktop window/dock icon, tray icon
    val logoRound: DrawableResource = Res.drawable.logo_round

    // the analytics `app` column, shared with every other app reporting into the same table —
    // see docs/Analytics.md. never derived from appName.
    // registered in the supabase repo's daily-digest APP_NAMES (/Users/jpage/working/projects/supabase)
    val analyticsId: String = "rtv"

    // primary accent color
    val brandColor: Color = Colors.Accent
}
