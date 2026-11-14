package com.jpage4500.reddittv

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeUIViewController
import com.jpage4500.reddittv.manager.AnalyticsManager
import com.jpage4500.reddittv.manager.AppLifecycle
import com.jpage4500.reddittv.manager.LogManager
import com.jpage4500.reddittv.util.IOSLogWriter
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.installCrashHandler
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIViewController

private val log = LoggerConfig.createLogger("MainViewController")

// NSNotificationCenter's block API hands back an opaque observer the caller is expected to hold on
// to; these live for the process, so a file-level list is the whole of the lifecycle management
private val lifecycleObservers = mutableListOf<Any>()

@OptIn(ExperimentalComposeUiApi::class)
fun MainViewController(): UIViewController {
    // initialize logger with ios file log writer
    LoggerConfig.initialize(IOSLogWriter())
    // armed before onAppStart(), which uploads the crash file the previous run left behind
    installCrashHandler()
    LogManager.onAppStart()
    AnalyticsManager.onAppStart()
    observeAppLifecycle()

    val controller = ComposeUIViewController(
        configure = {
            // disable safe area insets to render edge-to-edge
            // this allows content to render behind the status bar
            opaque = false
        }
    ) {
        App()
    }

    return controller
}

/**
 * report each foreground stretch to [AppLifecycle], which stops the app's timers on the way out and
 * starts them again on the way in, and tells analytics either way. iOS kills a backgrounded app
 * whenever it likes, so the exit event has to go out while there's still time to send it —
 * didEnterBackground is where that happens, not a termination handler.
 */
private fun observeAppLifecycle() {
    if (lifecycleObservers.isNotEmpty()) return

    val center = NSNotificationCenter.defaultCenter
    val queue = NSOperationQueue.mainQueue

    lifecycleObservers += center.addObserverForName(
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        queue = queue,
    ) { _ ->
        log.d("observeAppLifecycle: app in BACKGROUND")
        AppLifecycle.onBackground()
    }

    lifecycleObservers += center.addObserverForName(
        name = UIApplicationWillEnterForegroundNotification,
        `object` = null,
        queue = queue,
    ) { _ ->
        log.d("observeAppLifecycle: app in FOREGROUND")
        AppLifecycle.onForeground()
    }
}
