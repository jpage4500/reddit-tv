package com.jpage4500.reddittv

// For handling keyboard shortcuts (Cmd/Ctrl + Key)
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.*
import com.jpage4500.reddittv.data.AppPref
import com.jpage4500.reddittv.data.Branding
import com.jpage4500.reddittv.data.Rect
import com.jpage4500.reddittv.manager.AnalyticsManager
import com.jpage4500.reddittv.manager.AppEvents
import com.jpage4500.reddittv.manager.LogManager
import com.jpage4500.reddittv.ui.components.DesktopBack
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.util.*
import com.kdroid.composetray.tray.api.ExperimentalTrayAppApi
import com.kdroid.composetray.tray.api.TrayApp
import com.kdroid.composetray.tray.api.rememberTrayAppState
import com.kdroid.composetray.utils.IconRenderProperties
import com.kdroid.composetray.utils.isMenuBarInDarkMode
import reddittv.composeapp.generated.resources.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.system.exitProcess

private val log = LoggerConfig.createLogger("main")

@Volatile
var lastKnownRect: Rect? = null

@OptIn(ExperimentalTrayAppApi::class)
fun main(args: Array<String>) {
    if (DesktopUtils.isDesktopOS(DesktopOS.MAC)) {
        try {
            // set app name in menu bar (otherwise shows up as "MainKt")
            System.setProperty("apple.awt.application.name", Branding.appName)
        } catch (e: Exception) {
        }
    }

    // once per process, and deliberately OUTSIDE application {} - that block is a composable, and
    // showing/hiding the window or toggling "minimize to tray" recomposes it. run from in there and
    // every tray toggle re-logs APP_STARTED, re-uploads pending crashes and counts another launch.
    val logsDir = DesktopUtils.getHomeDir("logs")
    logsDir.mkdirs()
    LoggerConfig.initialize(DesktopAppLogWriter())
    // arm crash handler BEFORE onAppStart() so it's in place for any imminent crash.
    installCrashHandler()
    LogManager.onAppStart()
    // desktop has no background/foreground: the shutdown hook below writes the end time and the
    // next launch sends it
    AnalyticsManager.onAppStart()
    // the sign-ins live in the browser's profile, and it's slow to start: read them now, so the
    // first link or Settings screen doesn't wait on it
    CoroutineScope(Dispatchers.IO).launch { WebSession.refresh() }

    application {
        var isWindowVisible by remember { mutableStateOf(true) }
        // bumped by the tray's Open: the window may already be visible (just behind other apps), so
        // isWindowVisible alone wouldn't change and nothing would bring it forward
        var openRequests by remember { mutableIntStateOf(0) }
        val openWindow: () -> Unit = {
            isWindowVisible = true
            openRequests++
        }
        // load the minimize to tray preference (default to false)
        var minimizeToTray by remember { mutableStateOf(SettingsManager.getBoolean(AppPref.MINIMIZE_TO_TRAY)) }

        // restore window size and position from settings
        val rect = SettingsManager.getString(AppPref.WINDOW_RECT)
        val savedRect = JsonUtils.fromJson<Rect>(rect)
        val savedWidth = savedRect?.width ?: 1200
        val savedHeight = savedRect?.height ?: 800
        val savedX = savedRect?.x ?: -1
        val savedY = savedRect?.y ?: -1
        log.d("main: restoring window position - x=$savedX, y=$savedY, width=$savedWidth, height=$savedHeight")

        val windowState = rememberWindowState(
            size = DpSize(savedWidth.dp, savedHeight.dp),
            position = if (savedX >= 0 && savedY >= 0) {
                WindowPosition(savedX.dp, savedY.dp)
            } else {
                WindowPosition.Aligned(Alignment.Center)
            }
        )
        // the preview player takes the window full screen through this
        PlatformUtils.windowState = windowState

        // add shutdown hook to save window state on all exit scenarios (including CMD+Q on macOS).
        // remember{} so a recomposition of this block doesn't register a second copy
        remember {
            Runtime.getRuntime().addShutdownHook(Thread {
                try {
                    lastKnownRect?.let { rect ->
                        log.d("main: shutdown hook - saving window position - x=${rect.x}, y=${rect.y}, width=${rect.width}, height=${rect.height}")
                        SettingsManager.setString(AppPref.WINDOW_RECT, JsonUtils.toJson(rect))
                    }
                    // a settings write, not a POST — a shutdown hook has no time budget to spend on
                    // the network. this is what makes the next launch's exit event a clean one
                    AnalyticsManager.onAppExit()
                } catch (e: Exception) {
                    log.e("main: shutdown hook - exception: ${e.message}")
                }
            })
        }

        val iconContent: @Composable () -> Unit = remember { { TrayIcon() } }
        // the native tray menu below isn't composable, so its labels are resolved here
        val openAppLabel = stringResource(Res.string.open_app, Branding.appName)
        val minimizeLabel = stringResource(Res.string.minimize_to_tray)
        val exitLabel = stringResource(Res.string.exit)

        // tray icon with custom compose menu
        TrayApp(
            iconContent = iconContent,
            iconRenderProperties = trayIconProperties(),
            tooltip = Branding.appName,
            state = rememberTrayAppState(),
            menu = {
                // native fallback menu — used on Linux where the rich Compose menu
                // can't render (AppIndicator/StatusNotifierItem only accepts DBus-
                // described items, not arbitrary Compose UI). keep this in rough sync
                // with AppTrayMenu below so Linux users get the key actions.
                Item(
                    label = openAppLabel,
                    onClick = openWindow
                )
                Divider()
                CheckableItem(
                    label = minimizeLabel,
                    checked = minimizeToTray,
                    onCheckedChange = { isChecked ->
                        minimizeToTray = isChecked
                        SettingsManager.setBoolean(AppPref.MINIMIZE_TO_TRAY, isChecked)
                    },
                )
                Divider()
                Item(
                    label = exitLabel,
                    onClick = { exitProcess(0) }
                )
            }
        ) {
            AppTrayMenu(
                minimizeToTray = minimizeToTray,
                onMinimizeToTrayChanged = { isChecked ->
                    minimizeToTray = isChecked
                    SettingsManager.setBoolean(AppPref.MINIMIZE_TO_TRAY, isChecked)
                },
                onOpenApp = openWindow,
                onExit = { exitProcess(0) }
            )
        }

        Window(
            visible = isWindowVisible,
            onCloseRequest = {
                // check the setting before deciding what to do
                if (minimizeToTray) {
                    log.d("main: onCloseRequest - minimizing to tray")
                    isWindowVisible = false
                } else {
                    log.d("main: onCloseRequest - exiting app")
                    exitApplication()
                }
            },
            state = windowState,
            title = Branding.appName,
            icon = painterResource(Branding.logoRound),
            alwaysOnTop = false,
            // Escape is desktop's back button (BackPress)
            onPreviewKeyEvent = { event ->
                event.type == KeyEventType.KeyDown && event.key == Key.Escape && DesktopBack.onEscape()
            },
        ) {

            // track valid window positions so the shutdown hook can persist the last one
            LaunchedEffect(windowState.position, windowState.size) {
                snapshotFlow {
                    if (windowState.position.isSpecified && windowState.size.isSpecified) {
                        Rect(
                            x = windowState.position.x.value.toInt(),
                            y = windowState.position.y.value.toInt(),
                            width = windowState.size.width.value.toInt(),
                            height = windowState.size.height.value.toInt()
                        )
                    } else null
                }.collect { validRect ->
                    if (validRect != null) {
                        lastKnownRect = validRect
                    }
                }
            }

            // bring window to front on app launch AND when restored from tray — pinned on top only
            // for the restore, since at launch the pin is just a flash (see bringWindowToFront)
            var isLaunched by remember { mutableStateOf(false) }
            LaunchedEffect(isWindowVisible) {
                if (isWindowVisible) {
                    DesktopUtils.bringWindowToFront(window, pinOnTop = isLaunched)
                }
                isLaunched = true
            }

            // the tray's Open — also when the window is visible but behind other apps
            LaunchedEffect(openRequests) {
                if (openRequests == 0) return@LaunchedEffect
                if (windowState.isMinimized) windowState.isMinimized = false
                DesktopUtils.bringWindowToFront(window)
            }

            MenuBar {
                Menu(stringResource(Res.string.menu_file)) {
                    Item(
                        text = stringResource(Res.string.settings),
                        shortcut = DesktopUtils.getPlatformShortcut(Key.Comma),
                        onClick = { AppEvents.openSettings.tryEmit(Unit) },
                    )

                    Item(
                        text = stringResource(Res.string.exit),
                        shortcut = DesktopUtils.getPlatformShortcut(Key.W),
                        onClick = { exitApplication() }
                    )
                }

                // the standard Mac menu; Windows and Linux have these on the title bar
                if (DesktopUtils.isDesktopOS(DesktopOS.MAC)) {
                    Menu(stringResource(Res.string.menu_window)) {
                        Item(
                            text = stringResource(Res.string.menu_minimize),
                            shortcut = DesktopUtils.getPlatformShortcut(Key.M),
                            onClick = { windowState.isMinimized = true },
                        )
                        Item(
                            text = stringResource(Res.string.menu_zoom),
                            onClick = {
                                windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                                    WindowPlacement.Floating
                                } else {
                                    WindowPlacement.Maximized
                                }
                            },
                        )
                    }
                }

                Menu(stringResource(Res.string.help)) {
                    Item(
                        text = stringResource(Res.string.menu_github, Branding.appName),
                        onClick = { PlatformUtils.openUrl(Branding.githubUrl) },
                    )
                }
            }

            App()
        }
    }
}

private fun trayIconProperties(): IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem()

/**
 * the menu bar / tray icon. black or white to match the menu bar rather than the app theme - this is
 * the OS's furniture, and a light menu bar with a white icon on it is an empty slot.
 */
@Composable
private fun TrayIcon() {
    val tint = if (isMenuBarInDarkMode()) Color.White else Color.Black
    Icon(
        painter = painterResource(Res.drawable.icon_tv),
        contentDescription = "",
        tint = tint,
        modifier = Modifier.fillMaxSize().padding(6.dp)
    )
}

@Composable
fun AppTrayMenu(
    minimizeToTray: Boolean,
    onMinimizeToTrayChanged: (Boolean) -> Unit,
    onOpenApp: () -> Unit,
    onExit: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier
                .width(220.dp)
                .wrapContentHeight(),
            color = Colors.PrimaryBlue,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                MenuButton(
                    label = stringResource(Res.string.open_app, Branding.appName),
                    onClick = onOpenApp
                )

                HorizontalDivider()

                // "Minimize to tray" checkbox item
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMinimizeToTrayChanged(!minimizeToTray) }
                        .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.minimize_to_tray),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )

                    Checkbox(
                        checked = minimizeToTray,
                        onCheckedChange = { onMinimizeToTrayChanged(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.White,
                            checkmarkColor = Colors.PrimaryBlue,
                            uncheckedColor = Color.LightGray
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                }

                HorizontalDivider()

                MenuButton(
                    label = stringResource(Res.string.exit),
                    onClick = onExit
                )
            }
        }
    }
}

@Composable
fun MenuButton(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
    }
}
