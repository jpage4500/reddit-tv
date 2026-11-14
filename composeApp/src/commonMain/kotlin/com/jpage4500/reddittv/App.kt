package com.jpage4500.reddittv

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.jpage4500.reddittv.manager.UpdateManager
import com.jpage4500.reddittv.ui.RootScreen
import com.jpage4500.reddittv.ui.components.FocusIndication
import com.jpage4500.reddittv.ui.theme.RedditColorScheme
import com.jpage4500.reddittv.util.ImageLoaderConfig
import com.jpage4500.reddittv.util.PlatformUtils
import com.jpage4500.reddittv.util.Utils
import com.jpage4500.reddittv.util.ToastHost
import reddittv.composeapp.generated.resources.Res
import reddittv.composeapp.generated.resources.settings
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.getString

@Composable
fun App() {
    // register Coil's singleton ImageLoader. on Android this is a no-op (already set by
    // MainApplication.onCreate); on desktop/iOS this is the primary init
    remember { ImageLoaderConfig.registerSingleton() }

    // warm the compose-resources string table — until it lands `stringResource` answers ""
    LaunchedEffect(Unit) {
        getString(Res.string.settings)
    }

    // one app for phone and TV: the TV gets the grid and the remote-friendly focus ring
    val isTv = remember { PlatformUtils.isTv() }

    // low-priority: check for app updates (sideloaded Android / desktop only)
    if (UpdateManager.isUpdateCheckNeeded()) {
        LaunchedEffect(Unit) {
            delay(30_000)
            UpdateManager.checkForUpdatesIfDue()
        }
    }

    // a remote or a keyboard needs to see where it is — the ripple's focus tint is all but invisible
    val isFocusDriven = remember { Utils.isFocusDriven() }

    MaterialTheme(colorScheme = RedditColorScheme) {
        if (isFocusDriven) {
            CompositionLocalProvider(LocalIndication provides FocusIndication) {
                RootScreen(isTv = isTv)
            }
        } else {
            RootScreen(isTv = isTv)
        }
        ToastHost()
    }
}
