package com.jpage4500.reddittv.ui.dialog

// import com.jpage4500.reddittv.manager.AnalyticsManager
import androidx.compose.runtime.*
import reddittv.composeapp.generated.resources.*
import com.jpage4500.reddittv.APP_VERSION
import com.jpage4500.reddittv.BUILD_DATE
import com.jpage4500.reddittv.data.AppPref
import com.jpage4500.reddittv.data.Branding
import com.jpage4500.reddittv.manager.LogManager
import com.jpage4500.reddittv.manager.UpdateManager
import com.jpage4500.reddittv.ui.dialog.common.TextInputDialog
import com.jpage4500.reddittv.ui.setting.SettingItem
import com.jpage4500.reddittv.ui.setting.SettingSwitchItem
import com.jpage4500.reddittv.util.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.*

private enum class AboutItem(
    val labelRes: StringResource,
    val iconRes: DrawableResource,
) {
    SUPPORT(Res.string.support, Res.drawable.icon_help),
    // the daily check on or off — checking now is in the nav menu. sideloaded builds only
    // (UpdateManager.isEnabled)
    AUTO_UPDATE_CHECK(Res.string.check_for_updates, Res.drawable.icon_update),
    // ANALYTICS: the Share Usage Data opt-out. commented out rather than deleted — the events now
    // only go out from desktop and sideloaded android builds (see AnalyticsManager.isReportingBuild),
    // so there is nothing for a Play Store or App Store user to switch off. put this back if the
    // store builds ever start reporting again; the strings, the pref and
    // AnalyticsManager.setEnabled() are all still here.
    // ANALYTICS(Res.string.analytics, Res.drawable.icon_backup),
    DEBUG_MODE(Res.string.debug_mode, Res.drawable.icon_debug),
    VIEW_LOGS(Res.string.view_logs, Res.drawable.icon_logs),
    DETAILS(Res.string.details, Res.drawable.icon_info),
}

/**
 * dialog for about/support options
 */
@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    val isDebugMode by SettingsManager.getBoolAsState(AppPref.DEBUG_MODE)
    val isAutoUpdateCheck by SettingsManager.getBoolAsState(AppPref.AUTO_UPDATE_CHECK)
    // val isAnalyticsEnabled = SettingsManager.getBoolean(AppPref.ANALYTICS_ENABLED)
    val menuItems = AboutItem.entries.filter {
        (it != AboutItem.AUTO_UPDATE_CHECK || UpdateManager.isEnabled)
    }
    var selectedItem by remember { mutableStateOf<AboutItem?>(null) }

    BaseDialog(
        title = stringResource(Res.string.about),
        icon = Res.drawable.icon_info,
        onDismiss = onDismiss,
    ) {
        menuItems.forEach { item ->
            val showDivider = item != menuItems.last()
            // only DEBUG_MODE and AUTO_UPDATE_CHECK are toggles; the other items open a sub-dialog
            when (item) {
                AboutItem.AUTO_UPDATE_CHECK -> SettingSwitchItem(
                    icon = item.iconRes,
                    labelRes = item.labelRes,
                    initialChecked = isAutoUpdateCheck,
                    showDivider = showDivider,
                    onCheckedChange = { newValue ->
                        SettingsManager.setBoolean(AppPref.AUTO_UPDATE_CHECK, newValue)
                    }
                )

                // AboutItem.ANALYTICS -> SettingSwitchItem(
                //     icon = item.iconRes,
                //     labelRes = item.labelRes,
                //     descRes = Res.string.analytics_desc,
                //     initialChecked = isAnalyticsEnabled,
                //     showDivider = showDivider,
                //     onCheckedChange = { newValue ->
                //         // not a plain pref write: opting out also drops the install id and any
                //         // queued rows (see AnalyticsManager.setEnabled)
                //         AnalyticsManager.setEnabled(newValue)
                //     }
                // )

                AboutItem.DEBUG_MODE -> SettingSwitchItem(
                    icon = item.iconRes,
                    labelRes = item.labelRes,
                    initialChecked = isDebugMode,
                    showDivider = showDivider,
                    onCheckedChange = { newValue ->
                        SettingsManager.setBoolean(AppPref.DEBUG_MODE, newValue)
                    }
                )

                AboutItem.DETAILS -> SettingItem(
                    icon = item.iconRes,
                    labelRes = item.labelRes,
                    valueText = APP_VERSION,
                    showDivider = showDivider,
                    onClick = {
                        selectedItem = item
                    }
                )

                else -> SettingItem(
                    icon = item.iconRes,
                    labelRes = item.labelRes,
                    showDivider = showDivider,
                    onClick = {
                        selectedItem = item
                    }
                )
            }
        }
    }

    when (selectedItem) {
        AboutItem.SUPPORT -> SupportDialog(onDismiss = { selectedItem = null })

        AboutItem.VIEW_LOGS -> {
            LogsDialog(
                onDismiss = { selectedItem = null }
            )
        }

        AboutItem.DETAILS -> {
            DetailsDialog(
                onDismiss = { selectedItem = null }
            )
        }

        else -> {}
    }
}

/**
 * a message to the developer, sent with this run's logs. from About and the phone's drawer
 */
@Composable
fun SupportDialog(
    onDismiss: () -> Unit,
) {
    TextInputDialog(
        title = stringResource(Res.string.support),
        okText = stringResource(Res.string.send),
        placeholder = stringResource(Res.string.support_hint),
        validator = {
            // NOTE: message is optional
            true
        },
        onDismiss = onDismiss,
        onConfirm = { text ->
            // upload logs
            LogManager.uploadLogs(
                text = text,
                listener = { result ->
                    if (result.isSuccess) {
                        ToastManager.showToast(Res.string.reported, success = true)
                    } else {
                        ToastManager.showToast(Res.string.report_failed, result.exceptionOrNull()?.message.orEmpty(), success = false)
                    }
                }
            )
        }
    )
}

/**
 * app info: version, build date, platform, the address phones reach a TV on, and a link to the
 * github page
 */
@Composable
private fun DetailsDialog(
    onDismiss: () -> Unit
) {
    val githubUrl = "https://github.com/${Branding.githubRepo}"
    BaseDialog(
        title = Branding.appName,
        icon = Branding.logo,
        onDismiss = onDismiss,
    ) {
        // info rows do nothing on click; long-press still toasts the value (SettingItem default)
        SettingItem(
            icon = Res.drawable.icon_info,
            labelRes = Res.string.version,
            valueText = APP_VERSION,
            onClick = {},
        )
        SettingItem(
            icon = Res.drawable.icon_clock,
            labelRes = Res.string.build_date,
            valueText = BUILD_DATE,
            onClick = {},
        )
        SettingItem(
            icon = PlatformUtils.getPlatformIcon(),
            labelRes = Res.string.platform,
            valueText = remember { platformDescription() },
            onClick = {},
        )
        SettingItem(
            icon = Res.drawable.icon_help,
            labelRes = Res.string.github,
            valueText = Branding.githubRepo,
            showDivider = false,
            onClick = {
                PlatformUtils.openUrl(githubUrl)
            },
        )
    }
}

/**
 * readable OS + version, ie: "Android 16 (API 36)", "iOS 18.2", "macOS 15.1".
 * getPlatformName()/getPlatformVersion() mean different things per platform (android returns the
 * release and the SDK level), so they're only combined here rather than changed — the log header
 * and analytics read them as-is
 */
private fun platformDescription(): String {
    val name = PlatformUtils.getPlatformName()
    val version = PlatformUtils.getPlatformVersion()
    return when (PlatformUtils.getPlatform()) {
        PlatformType.ANDROID -> "Android $name (API $version)"
        PlatformType.IOS, PlatformType.WEB -> "$name $version"
        PlatformType.DESKTOP -> {
            val osName = when (name) {
                DesktopOS.MAC.name -> "macOS"
                DesktopOS.WINDOWS.name -> "Windows"
                else -> "Linux"
            }
            "$osName $version"
        }
    }
}

