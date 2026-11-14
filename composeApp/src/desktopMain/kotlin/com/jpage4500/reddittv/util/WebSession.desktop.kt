package com.jpage4500.reddittv.util

import com.jpage4500.reddittv.data.SignInSite
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

private val log = LoggerConfig.createLogger("WebSession")

// once signed in, a moment more for the redirects after it to set the rest of the session
private const val AFTER_SIGN_IN_MS = 2_000L

/**
 * the cookies of [CdpBrowser]'s profile — the one the sign-in window and PageSniffer's headless tabs
 * share. the browser only answers asynchronously, so this keeps a copy that [refresh] brings up to
 * date, and everything else reads that
 */
actual object WebSession {
    private data class BrowserCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val secure: Boolean,
    ) {
        // the browser's own rule: a leading dot covers subdomains, none means that host only
        fun matches(url: Url): Boolean {
            val host = url.host.lowercase()
            val domainMatch = if (domain.startsWith(".")) {
                host == domain.substring(1) || host.endsWith(domain)
            } else {
                host == domain
            }
            if (!domainMatch) return false
            if (secure && url.protocol != URLProtocol.HTTPS) return false
            val urlPath = url.encodedPath.ifEmpty { "/" }
            return urlPath == path || urlPath.startsWith(path.trimEnd('/') + "/") || path == "/"
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var jar: List<BrowserCookie> = emptyList()

    actual val isSupported: Boolean
        get() = CdpBrowser.executable != null

    actual val userAgent: String?
        get() = CdpBrowser.userAgent

    actual fun cookies(url: String): String? {
        val parsed = runCatching { Url(url) }.getOrNull() ?: return null
        return jar.filter { it.matches(parsed) }
            // longest path first, as a browser orders them
            .sortedByDescending { it.path.length }
            .joinToString("; ") { "${it.name}=${it.value}" }
            .takeIf { it.isNotEmpty() }
    }

    actual fun isSignedIn(site: SignInSite): Boolean =
        cookies(site.homeUrl).orEmpty().split(';').any { it.trim().startsWith("${site.sessionCookie}=") }

    actual fun signOut(site: SignInSite) {
        val home = Url(site.homeUrl)
        val gone = jar.filter { it.matches(home) }
        // straight away, so the list shows it signed out; the browser catches up below
        jar = jar - gone.toSet()
        scope.launch {
            try {
                val tab = CdpBrowser.connect().newTab()
                try {
                    gone.forEach { cookie ->
                        tab.send("Network.deleteCookies", buildJsonObject {
                            put("name", cookie.name)
                            put("domain", cookie.domain)
                            put("path", cookie.path)
                        })
                    }
                } finally {
                    withContext(NonCancellable) { tab.close() }
                }
                refresh()
                log.d("signOut: ${site.label}, ${gone.size} cookies, signed in now:${isSignedIn(site)}")
            } catch (e: Exception) {
                log.e("signOut: ${site.label}: ${e.message}")
            }
        }
    }

    // the browser writes its own cookies to disk, and does on Browser.close
    actual fun flush() {}

    /**
     * read the profile's cookies again. starts the headless browser if it isn't running — unless the
     * profile has never been used, when there's nothing to read
     */
    actual suspend fun refresh() {
        if (!isSupported || !CdpBrowser.hasProfile) return
        try {
            read(CdpBrowser.connect())
        } catch (e: Exception) {
            log.e("refresh: ${e.message}")
        }
    }

    // Storage.getCookies: every cookie in the profile, asked of the browser rather than a tab
    private suspend fun read(client: CdpClient) {
        val cookies = client.send("Storage.getCookies")["cookies"]?.jsonArray.orEmpty()
        jar = cookies.mapNotNull { element ->
            val cookie = element.jsonObject
            BrowserCookie(
                name = cookie["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                value = cookie["value"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                domain = cookie["domain"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: return@mapNotNull null,
                path = cookie["path"]?.jsonPrimitive?.contentOrNull ?: "/",
                secure = cookie["secure"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
    }

    /**
     * [url] in a browser window for the user to sign in to [site] with. returns once they have (and
     * closes the window), or once they close it themselves
     * @return whether the user is signed in now
     * @throws CdpException when the window couldn't be opened
     */
    suspend fun signIn(site: SignInSite, url: String): Boolean {
        try {
            val client = CdpBrowser.openWindow(url)
            log.d("signIn: ${site.label}, window open")
            try {
                var signedInAt: Long? = null
                while (true) {
                    delay(1_000)
                    read(client)
                    if (isSignedIn(site)) {
                        val at = signedInAt ?: DateUtils.nowMs().also { signedInAt = it }
                        if (DateUtils.nowMs() - at >= AFTER_SIGN_IN_MS) break
                    }
                    // macOS keeps the browser running with no window left, so count them
                    val pages = client.send("Target.getTargets")["targetInfos"]?.jsonArray.orEmpty()
                        .count { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "page" }
                    if (pages == 0) {
                        log.d("signIn: ${site.label}, window closed")
                        break
                    }
                }
                read(client)
            } catch (e: CdpException) {
                // closing the last window ends the browser on Windows and Linux: the socket goes
                log.d("signIn: ${site.label}: ${e.message}")
            }
        } finally {
            withContext(NonCancellable) { CdpBrowser.close() }
        }
        val signedIn = isSignedIn(site)
        log.d("signIn: ${site.label}, signed in:$signedIn")
        return signedIn
    }
}
