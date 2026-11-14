package com.jpage4500.reddittv.util

import com.jpage4500.reddittv.data.SignInSite

// no sign-in browser here yet: signed-in sites fall back to the plain fetch
actual object WebSession {
    actual val isSupported: Boolean = false
    actual val userAgent: String? = null
    actual fun cookies(url: String): String? = null
    actual fun isSignedIn(site: SignInSite): Boolean = false
    actual fun signOut(site: SignInSite) {}
    actual fun flush() {}
    actual suspend fun refresh() {}
}
