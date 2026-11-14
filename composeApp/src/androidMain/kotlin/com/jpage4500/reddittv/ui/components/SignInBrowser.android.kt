package com.jpage4500.reddittv.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.jpage4500.reddittv.data.SignInSite
import com.jpage4500.reddittv.util.WebSession

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun SignInBrowser(url: String, modifier: Modifier, script: String?, onDone: () -> Unit, onPageLoaded: () -> Unit) {
    // the WebView outlives recompositions: read the latest values from its callbacks
    val currentScript by rememberUpdatedState(script)
    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnPageLoaded by rememberUpdatedState(onPageLoaded)
    var isDone by remember { mutableStateOf(false) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                // AndroidView defaults to WRAP_CONTENT, which makes WebView pin the page's viewport
                // height to 0: pages sized in vh (Instagram) collapse and show nothing
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                // keep every page (a login's redirects included) in here, not in a browser app.
                // [script] runs once each page is loaded (the phone sign-in's autofill)
                webViewClient = object : WebViewClient() {
                    // signed in: the session cookie is there once reddit leaves the login page.
                    // checked as each page starts (and on in-page navigations), not only when it
                    // finishes: reddit's home page can take 30s+ to finish loading on a Fire TV
                    fun checkDone(pageUrl: String?) {
                        if (!isDone && pageUrl != null && !pageUrl.contains("/login") &&
                            WebSession.isSignedIn(SignInSite.REDDIT)
                        ) {
                            isDone = true
                            WebSession.flush()
                            currentOnDone()
                        }
                    }

                    override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) = checkDone(pageUrl)

                    override fun doUpdateVisitedHistory(view: WebView, pageUrl: String?, isReload: Boolean) = checkDone(pageUrl)

                    override fun onPageFinished(view: WebView, pageUrl: String?) {
                        currentScript?.let { view.evaluateJavascript(it, null) }
                        currentOnPageLoaded()
                        checkDone(pageUrl)
                    }
                }
                loadUrl(url)
            }
        },
        onRelease = { webView ->
            // the session has to reach disk before the process can die
            WebSession.flush()
            webView.stopLoading()
            webView.destroy()
        },
    )
}
