package com.jpage4500.reddittv

import android.app.Application
import com.jpage4500.reddittv.manager.AnalyticsManager
import com.jpage4500.reddittv.manager.LogManager
import com.jpage4500.reddittv.util.AndroidContextProvider
import com.jpage4500.reddittv.util.AndroidLogWriter
import com.jpage4500.reddittv.util.ImageLoaderConfig
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.installCrashHandler

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidContextProvider.init(this)

        LoggerConfig.initialize(AndroidLogWriter())
        // arm the crash handler BEFORE onAppStart(), which uploads a crash the previous run left
        installCrashHandler()
        LogManager.onAppStart()
        AnalyticsManager.onAppStart()

        // register Coil's singleton ImageLoader before any Activity runs
        ImageLoaderConfig.registerSingleton()
    }
}
