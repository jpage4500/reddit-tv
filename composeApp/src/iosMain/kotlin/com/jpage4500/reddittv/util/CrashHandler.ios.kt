package com.jpage4500.reddittv.util

import com.jpage4500.reddittv.manager.LogManager
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual fun installCrashHandler() {
    setUnhandledExceptionHook { throwable ->
        LogManager.recordCrash(throwable, "ios")
    }
}
