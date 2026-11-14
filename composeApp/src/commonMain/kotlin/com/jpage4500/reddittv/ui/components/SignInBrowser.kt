package com.jpage4500.reddittv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * a real, visible browser at [url], for signing in to a site. it shares cookies with
 * [com.jpage4500.reddittv.util.WebSession] and PageSniffer's hidden browser, so the session it makes is
 * the one the app fetches with. Android: a WebView. desktop: a window of the Chrome or Edge it
 * drives, since the page can't sit in the app's own. iOS: not built
 * ([com.jpage4500.reddittv.util.WebSession.isSupported] is false, so nothing shows it)
 *
 * @param onDone for a browser that can tell the user has finished (desktop: signed in, or closed
 * the window) — Android leaves that to the user closing the dialog
 */
@Composable
expect fun SignInBrowser(
    url: String,
    modifier: Modifier = Modifier,
    // run on each page load (android only), ie: [com.jpage4500.reddittv.manager.loginAutofillScript]
    script: String? = null,
    onDone: () -> Unit = {},
    // a page in it has finished loading (android only): what's covering it can go
    onPageLoaded: () -> Unit = {},
)
