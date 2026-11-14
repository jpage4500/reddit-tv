package com.jpage4500.reddittv.util

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

private val log = LoggerConfig.createLogger("CdpClient")

// how long a launched browser gets to write DevToolsActivePort
private const val LAUNCH_TIMEOUT_MS = 15_000L

// a reply that takes longer than this means the browser is stuck, not busy
private const val SEND_TIMEOUT_MS = 30_000L

class CdpException(message: String) : Exception(message)

/**
 * something the browser sent without being asked
 * @param sessionId the tab (or frame, or worker) it's about; null for the browser itself
 */
data class CdpEvent(val method: String, val sessionId: String?, val params: JsonObject)

/**
 * the user's Chrome or Edge, run headless in the app's own profile and driven over the DevTools
 * protocol (CDP) — desktop's hidden browser, since the JVM has no WebView of its own.
 *
 * launched on first use and kept for the life of the app. the profile ([DesktopUtils.getHomeDir]
 * `/browser`) is never the user's own: their browser can stay open alongside it, and its cookies
 * are the app's.
 *
 * one browser runs on the profile at a time, so signing in ([openWindow]) closes the headless one
 * and starts it again with a window; [close] ends that, and the next [connect] is headless again
 */
object CdpBrowser {
    private val mutex = Mutex()
    private var client: CdpClient? = null
    // the one this app started, if it did — so it can be waited on, and stopped on exit
    @Volatile
    private var process: ProcessHandle? = null

    private val profileDir: File get() = DesktopUtils.getHomeDir("browser")

    /** Chrome, Chromium or Edge, whichever is installed first; null if none is */
    val executable: File? by lazy {
        findExecutable().also { log.d("executable: ${it?.absolutePath ?: "none found"}") }
    }

    /** the running browser's User-Agent, once one has started */
    @Volatile
    var userAgent: String? = null
        private set

    init {
        Runtime.getRuntime().addShutdownHook(Thread { process?.destroy() })
    }

    /** true once a browser has used the profile: until then there's no cookie to find */
    val hasProfile: Boolean
        get() = File(profileDir, "Default").isDirectory

    /**
     * the running browser, starting a headless one if none is. a sign-in window that's open is used
     * as it is, rather than closing it under the user
     */
    suspend fun connect(): CdpClient = mutex.withLock {
        client?.takeIf { it.isOpen }?.let { return it }
        // one still running on our profile (left behind by a crash, say) answers on the port it
        // wrote. a second launch on the same profile would only hand its arguments to that one and
        // exit
        withContext(Dispatchers.IO) { reuseRunning() ?: launch(headless = true, url = null) }.also { client = it }
    }

    /** a window at [url], for the user to sign in with — the headless browser gives way to it */
    suspend fun openWindow(url: String): CdpClient = mutex.withLock {
        val running = client?.takeIf { it.isOpen } ?: withContext(Dispatchers.IO) { reuseRunning() }
        if (running != null && !running.isHeadless) {
            running.send("Target.createTarget", buildJsonObject {
                put("url", url)
                put("newWindow", true)
            })
            client = running
            return running
        }
        running?.let { stop(it) }
        withContext(Dispatchers.IO) { launch(headless = false, url = url) }.also { client = it }
    }

    /** close the browser, and with it any window; the next [connect] starts a headless one */
    suspend fun close() = mutex.withLock {
        client?.takeIf { it.isOpen }?.let { stop(it) }
        client = null
    }

    // Browser.close, not a kill: that's what writes the cookies to disk
    private suspend fun stop(running: CdpClient) {
        log.d("stop: headless:${running.isHeadless}")
        try {
            running.send("Browser.close")
        } catch (e: CdpException) {
            // the socket closing before the reply is how it usually ends
        }
        withContext(Dispatchers.IO) {
            val started = process
            if (started != null) {
                val exited = try {
                    started.onExit().get(5, TimeUnit.SECONDS)
                    true
                } catch (e: TimeoutException) {
                    false
                }
                if (!exited) started.destroy()
                process = null
            } else {
                withTimeoutOrNull(5_000) { while (running.isOpen) delay(100) }
                // not ours to wait on: give it a moment to let go of the profile
                delay(1_000)
            }
        }
    }

    private suspend fun reuseRunning(): CdpClient? {
        val url = readDevToolsUrl() ?: return null
        return try {
            CdpClient.open(url).also {
                userAgent = it.userAgent
                log.d("reuseRunning: $url, headless:${it.isHeadless}")
            }
        } catch (e: Exception) {
            File(profileDir, "DevToolsActivePort").delete()
            null
        }
    }

    /** @param url the window to open with; headless opens a blank page */
    private suspend fun launch(headless: Boolean, url: String?): CdpClient {
        val exe = executable ?: throw CdpException("no Chrome or Edge installed")
        profileDir.mkdirs()
        val command = mutableListOf(
            exe.absolutePath,
            // 0: the OS picks a free port, and the browser writes it to DevToolsActivePort
            "--remote-debugging-port=0",
            "--user-data-dir=${profileDir.absolutePath}",
            "--no-first-run",
            "--no-default-browser-check",
            // cookies encrypted with a key of the profile's own rather than the OS keychain's, so
            // macOS never asks to let it at "Chrome Safe Storage". both modes have to agree, or one
            // can't read the other's sign-in
            "--use-mock-keychain",
            "--password-store=basic",
        )
        if (headless) {
            command += listOf(
                "--headless=new",
                // lets __ctPlay() start a video without a click
                "--autoplay-policy=no-user-gesture-required",
                "--mute-audio",
                // navigator.webdriver stays false, which some players check before loading. not
                // with a window, where Chrome warns about the flag in a bar across the page
                "--disable-blink-features=AutomationControlled",
                "about:blank",
            )
        } else {
            // a bare window — no tabs or address bar — sized like a phone's sign-in page
            command += listOf("--app=$url", "--window-size=520,760")
        }

        // the previous browser can take a moment to let go of the profile; until it does, a launch
        // just hands its arguments over and exits
        repeat(3) { attempt ->
            File(profileDir, "DevToolsActivePort").delete()
            log.d("launch: ${exe.name}, headless:$headless, attempt:${attempt + 1}")
            // a process start time can be whole seconds
            val launchedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
            // null until the browser shows up, when it's started through LaunchServices
            var started = start(exe, command, headless)
            process = started

            val deadline = DateUtils.nowMs() + LAUNCH_TIMEOUT_MS
            while (DateUtils.nowMs() < deadline) {
                if (started == null) {
                    started = findLaunched(exe, launchedAt)
                    process = started
                } else if (!started.isAlive) {
                    break
                }
                readDevToolsUrl()?.let { devToolsUrl ->
                    return CdpClient.open(devToolsUrl).also { userAgent = it.userAgent }
                }
                delay(100)
            }
            if (started?.isAlive == true) {
                started.destroy()
                throw CdpException("${exe.name} didn't start within ${LAUNCH_TIMEOUT_MS}ms")
            }
            log.w("launch: ${exe.name} exited")
            process = null
            delay(1_000)
        }
        throw CdpException("${exe.name} wouldn't start")
    }

    /**
     * a macOS browser is opened through LaunchServices rather than as a child of this app: macOS
     * holds whoever started a process responsible for what it does, so Chrome updating its own
     * bundle would otherwise be blamed on this app ("Share to TV+ was prevented from modifying apps")
     *
     * @return the browser's process, or null for one opened on macOS — [findLaunched] finds that
     */
    private fun start(exe: File, command: List<String>, headless: Boolean): ProcessHandle? {
        if (!DesktopUtils.isDesktopOS(DesktopOS.MAC)) {
            return ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .toHandle()
        }
        // <name>.app/Contents/MacOS/<name>
        val bundle = exe.parentFile.parentFile.parentFile
        // -n: a new instance even when the user's own browser is running. -g: a headless one stays
        // out of the foreground; a sign-in window is meant to come to the front
        val open = mutableListOf("open", "-n")
        if (headless) open += "-g"
        open += listOf("-a", bundle.absolutePath, "--args")
        open += command.drop(1)
        val opener = ProcessBuilder(open)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (opener.waitFor(10, TimeUnit.SECONDS) && opener.exitValue() != 0) {
            log.w("start: open exited with ${opener.exitValue()}")
        }
        return null
    }

    // the browser process (not a helper, whose executable is another one) on our profile — or,
    // when its arguments can't be read, the one that started after the launch
    private fun findLaunched(exe: File, launchedAt: Instant): ProcessHandle? {
        val profileArg = "--user-data-dir=${profileDir.absolutePath}"
        return ProcessHandle.allProcesses().filter { handle ->
            val info = handle.info()
            if (info.command().orElse(null) != exe.absolutePath) return@filter false
            // a browser started from the Dock reports no arguments at all
            val args = info.arguments().orElse(null)
            if (!args.isNullOrEmpty()) args.contains(profileArg) else info.startInstant().orElse(Instant.MIN) >= launchedAt
        }.findFirst().orElse(null)
    }

    // line 1 is the port, line 2 the browser's WebSocket path
    private fun readDevToolsUrl(): String? {
        val lines = try {
            File(profileDir, "DevToolsActivePort").takeIf { it.exists() }?.readLines()
        } catch (e: Exception) {
            null
        } ?: return null
        val port = lines.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val path = lines.getOrNull(1)?.trim()?.takeIf { it.startsWith("/") } ?: return null
        return "ws://127.0.0.1:$port$path"
    }

    private fun findExecutable(): File? {
        val candidates = when (DesktopUtils.getDesktopOS()) {
            DesktopOS.MAC -> {
                val apps = listOf("/Applications", "${System.getProperty("user.home")}/Applications")
                val bundles = listOf("Google Chrome", "Chromium", "Microsoft Edge")
                apps.flatMap { dir -> bundles.map { File("$dir/$it.app/Contents/MacOS/$it") } }
            }

            DesktopOS.WINDOWS -> {
                val roots = listOf("ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA").mapNotNull { System.getenv(it) }
                val exes = listOf("Google\\Chrome\\Application\\chrome.exe", "Microsoft\\Edge\\Application\\msedge.exe")
                exes.flatMap { exe -> roots.map { File(it, exe) } }
            }

            DesktopOS.LINUX -> {
                val path = System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }
                val names = listOf("google-chrome", "google-chrome-stable", "chromium", "chromium-browser", "microsoft-edge", "microsoft-edge-stable")
                names.flatMap { name -> path.map { File(it, name) } }
            }
        }
        return candidates.firstOrNull { it.isFile && it.canExecute() }
    }
}

/**
 * one WebSocket to a browser's DevTools endpoint. commands are matched to their replies by id;
 * everything else arrives on [events]
 */
class CdpClient private constructor(
    private val http: HttpClient,
    private val socket: DefaultClientWebSocketSession,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

    // a busy page sends hundreds of network events a second; the oldest go if a collector lags
    private val _events = MutableSharedFlow<CdpEvent>(extraBufferCapacity = 4096, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<CdpEvent> = _events

    @Volatile
    var isOpen = true
        private set

    /** a browser run with --headless, which says so in its User-Agent */
    var isHeadless = false
        private set

    /** the browser's own User-Agent, with the "Headless" that gives it away taken out */
    var userAgent: String? = null
        private set

    init {
        scope.launch {
            try {
                for (frame in socket.incoming) {
                    if (frame is Frame.Text) onMessage(frame.readText())
                }
            } catch (e: Exception) {
                log.w("read: ${e.message}")
            }
            close("socket closed")
        }
    }

    private fun onMessage(text: String) {
        val message = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            log.w("onMessage: bad JSON: ${e.message}")
            return
        }
        val id = message["id"]?.jsonPrimitive?.intOrNull
        if (id != null) {
            pending.remove(id)?.complete(message)
            return
        }
        val method = message["method"]?.jsonPrimitive?.contentOrNull ?: return
        _events.tryEmit(
            CdpEvent(
                method = method,
                sessionId = message["sessionId"]?.jsonPrimitive?.contentOrNull,
                params = message["params"]?.jsonObject ?: JsonObject(emptyMap()),
            )
        )
    }

    /**
     * @param sessionId the tab to send it to; null for the browser itself
     * @return the reply's `result`
     * @throws CdpException when the browser answers with an error, or is gone
     */
    suspend fun send(method: String, params: JsonObject? = null, sessionId: String? = null): JsonObject {
        if (!isOpen) throw CdpException("$method: browser is gone")
        val id = nextId.getAndIncrement()
        val reply = CompletableDeferred<JsonObject>()
        pending[id] = reply
        val message = buildJsonObject {
            put("id", id)
            put("method", method)
            if (params != null) put("params", params)
            if (sessionId != null) put("sessionId", sessionId)
        }
        try {
            socket.send(Frame.Text(message.toString()))
            val response = withTimeoutOrNull(SEND_TIMEOUT_MS) { reply.await() } ?: throw CdpException("$method: no reply")
            response["error"]?.jsonObject?.let { throw CdpException("$method: ${it["message"]?.jsonPrimitive?.contentOrNull}") }
            return response["result"]?.jsonObject ?: JsonObject(emptyMap())
        } finally {
            pending.remove(id)
        }
    }

    /** a new, empty tab of its own */
    suspend fun newTab(): CdpTab {
        val targetId = send("Target.createTarget", buildJsonObject { put("url", "about:blank") })["targetId"]
            ?.jsonPrimitive?.contentOrNull ?: throw CdpException("createTarget: no targetId")
        val sessionId = send("Target.attachToTarget", buildJsonObject {
            put("targetId", targetId)
            // one socket for every tab: each message says which one it's for
            put("flatten", true)
        })["sessionId"]?.jsonPrimitive?.contentOrNull ?: throw CdpException("attachToTarget: no sessionId")
        return CdpTab(this, targetId, sessionId)
    }

    /** the tab a window opened with ([CdpBrowser.openWindow] at about:blank), rather than a new one beside it */
    suspend fun windowTab(): CdpTab {
        val pages = send("Target.getTargets")["targetInfos"]?.jsonArray.orEmpty().map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.contentOrNull == "page" }
        val page = pages.lastOrNull { it["url"]?.jsonPrimitive?.contentOrNull == "about:blank" } ?: pages.lastOrNull()
        val targetId = page?.get("targetId")?.jsonPrimitive?.contentOrNull ?: throw CdpException("windowTab: no window")
        val sessionId = send("Target.attachToTarget", buildJsonObject {
            put("targetId", targetId)
            put("flatten", true)
        })["sessionId"]?.jsonPrimitive?.contentOrNull ?: throw CdpException("attachToTarget: no sessionId")
        return CdpTab(this, targetId, sessionId)
    }

    private fun close(reason: String) {
        if (!isOpen) return
        isOpen = false
        log.d("close: $reason")
        pending.values.forEach { it.completeExceptionally(CdpException(reason)) }
        pending.clear()
        http.close()
        scope.cancel()
    }

    companion object {
        suspend fun open(url: String): CdpClient {
            val http = HttpClient(OkHttp) { install(WebSockets) }
            try {
                val client = CdpClient(http, http.webSocketSession(url))
                val raw = client.send("Browser.getVersion")["userAgent"]?.jsonPrimitive?.contentOrNull
                client.isHeadless = raw?.contains("HeadlessChrome") == true
                client.userAgent = raw?.replace("HeadlessChrome", "Chrome")
                return client
            } catch (e: Exception) {
                http.close()
                throw e
            }
        }
    }
}

/**
 * one tab, and the frames and workers it starts — a cross-site iframe (most embedded players) runs
 * in a process of its own, and only reports its requests through a session of its own
 */
class CdpTab internal constructor(
    private val client: CdpClient,
    private val targetId: String,
    val sessionId: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply { add(sessionId) }

    /** follow every frame and worker this tab starts, so [events] covers theirs too */
    suspend fun attachChildren() {
        // undispatched: subscribed before the command below can start anything
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.events.filter { it.method == "Target.attachedToTarget" && it.sessionId in sessions }.collect { event ->
                val child = event.params["sessionId"]?.jsonPrimitive?.contentOrNull ?: return@collect
                sessions.add(child)
                scope.launch {
                    // it's paused until told to run, so nothing it requests is missed. a worker may
                    // not support every domain: carry on without it
                    runCatching { client.send("Network.enable", sessionId = child) }
                    runCatching { client.send("Target.setAutoAttach", AUTO_ATTACH, child) }
                    runCatching { client.send("Runtime.runIfWaitingForDebugger", sessionId = child) }
                }
            }
        }
        send("Target.setAutoAttach", AUTO_ATTACH)
    }

    suspend fun send(method: String, params: JsonObject? = null): JsonObject = client.send(method, params, sessionId)

    /** [method]'s events from this tab and anything it started — collect undispatched, to miss none */
    fun events(method: String): Flow<JsonObject> =
        client.events.filter { it.method == method && it.sessionId in sessions }.map { it.params }

    /**
     * run [js] in the page
     * @return its result, if that's a string; null for anything else, or when the page is between
     * documents
     */
    suspend fun evaluate(js: String): String? =
        try {
            val result = send("Runtime.evaluate", buildJsonObject {
                put("expression", js)
                put("returnByValue", true)
            })
            (result["result"]?.jsonObject?.get("value") as? JsonPrimitive)?.takeIf { it.isString }?.content
        } catch (e: CdpException) {
            null
        }

    /** the Cookie header the browser would send to [url], or null */
    suspend fun cookies(url: String): String? =
        try {
            send("Network.getCookies", buildJsonObject { putJsonArray("urls") { add(url) } })["cookies"]?.jsonArray
                ?.mapNotNull { cookie ->
                    val name = cookie.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    "$name=${cookie.jsonObject["value"]?.jsonPrimitive?.contentOrNull.orEmpty()}"
                }
                ?.joinToString("; ")?.takeIf { it.isNotEmpty() }
        } catch (e: CdpException) {
            null
        }

    suspend fun close() {
        scope.cancel()
        try {
            client.send("Target.closeTarget", buildJsonObject { put("targetId", targetId) })
        } catch (e: CdpException) {
            log.w("close: ${e.message}")
        }
    }

    private companion object {
        val AUTO_ATTACH = buildJsonObject {
            put("autoAttach", true)
            put("waitForDebuggerOnStart", true)
            put("flatten", true)
        }
    }
}
