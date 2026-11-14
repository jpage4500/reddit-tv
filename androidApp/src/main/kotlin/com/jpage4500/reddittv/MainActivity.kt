package com.jpage4500.reddittv

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jpage4500.reddittv.manager.AppLifecycle
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.PlatformUtils.clearScreenActivity
import com.jpage4500.reddittv.util.PlatformUtils.setScreenActivity

private val log = LoggerConfig.createLogger("MainActivity")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // the app is dark on every screen, so the system bars draw light icons
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setScreenActivity(this)
        setContent {
            App()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // only if it's still ours — see PlatformUtils.clearScreenActivity
        clearScreenActivity(this)
    }

    override fun onStart() {
        super.onStart()
        AppLifecycle.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // a rotation tears the activity down and builds it again; that's not the user leaving
        if (!isChangingConfigurations) {
            log.d("onStop: app in BACKGROUND")
            AppLifecycle.onBackground()
        }
    }
}
