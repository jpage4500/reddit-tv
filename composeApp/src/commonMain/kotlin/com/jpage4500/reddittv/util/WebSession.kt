package com.jpage4500.reddittv.util

import com.jpage4500.reddittv.data.SignInSite

/**
 * the cookies of the app's own browser — the sign-in one and PageSniffer's hidden one share them —
 * for fetching a [SignInSite]'s posts as the signed-in user.
 *
 * Android: the process-wide CookieManager. desktop: the profile of the Chrome or Edge it drives,
 * read into a copy by [refresh]. iOS: not built (no sign-in there yet).
 */
expect object WebSession {
    val isSupported: Boolean

    /** the browser's User-Agent: a session is safest used by the client that made it */
    val userAgent: String?

    /** the Cookie header the browser would send to [url], or null */
    fun cookies(url: String): String?

    fun isSignedIn(site: SignInSite): Boolean

    /** drop the site's cookies (its session with them) */
    fun signOut(site: SignInSite)

    /** write cookies to disk, so a sign-in outlives the process */
    fun flush()

    /**
     * bring the cookies the other calls answer from up to date — only desktop keeps a copy, since
     * its browser answers asynchronously. call before a run of [isSignedIn] / [cookies]
     */
    suspend fun refresh()
}
