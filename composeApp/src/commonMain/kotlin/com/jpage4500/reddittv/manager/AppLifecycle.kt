package com.jpage4500.reddittv.manager

import com.jpage4500.reddittv.util.LoggerConfig

private val log = LoggerConfig.createLogger("AppLifecycle")

/**
 * the app moving between foreground and background, in one place.
 *
 * every schedule in the app is a coroutine loop on a manager's own scope, and a mobile OS leaves
 * those running once the user has left: the process stays alive, the loops keep firing, and nothing
 * comes of any of it — the screen the results would update isn't being looked at. on Android it's
 * worse than pointless, because with the screen off the OS blocks the app's traffic and every
 * request fails at once, which is how a night on a bedside table turns into a log full of
 * `Unable to resolve host`.
 *
 * so each platform reports the transition here and this fans it out; the managers stop their loops
 * on the way out and start them again on the way in.
 *
 * **[onForeground] is the refresh trigger too.** Coming back is exactly when what's on screen is
 * most likely to be stale, so [UpdateManager] checks for a new version if a day has passed.
 *
 * desktop calls neither one, which leaves it in the state it starts in: a desktop window is either
 * open or the process is gone, and there's no OS restriction to work around.
 */
object AppLifecycle {
    // starts **true**: the process only exists because someone opened the app. iOS never posts
    // willEnterForeground for a first launch — only for a return — so starting false would swallow
    // the first didEnterBackground as a transition that never happened, and with it the session's
    // exit event. Android's first onStart is then a repeat, which is what the guard is for: it's
    // also what an orientation change looks like, and a rotation is not the user coming back
    private var isForeground = true

    /** false once the user has left the app — when Android may block its traffic */
    val isInForeground: Boolean
        get() = isForeground

    fun onForeground() {
        if (isForeground) return
        isForeground = true
        log.d("onForeground")

        AnalyticsManager.onAppForeground()
        UpdateManager.onForeground()
    }

    fun onBackground() {
        if (!isForeground) return
        isForeground = false
        log.d("onBackground")

        AnalyticsManager.onAppBackground()
    }
}
