package com.jpage4500.reddittv.manager

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * app-wide one-shot events from outside the compose tree (desktop menu bar, etc)
 */
object AppEvents {
    // desktop File > Settings
    val openSettings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
