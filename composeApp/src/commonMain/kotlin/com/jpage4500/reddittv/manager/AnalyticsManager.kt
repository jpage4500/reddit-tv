package com.jpage4500.reddittv.manager

import com.jpage4500.reddittv.APP_VERSION
import com.jpage4500.reddittv.IS_RELEASE
import com.jpage4500.reddittv.LOGGING_API
import com.jpage4500.reddittv.LOGGING_URL
import com.jpage4500.reddittv.data.AppPref
import com.jpage4500.reddittv.data.Branding
import com.jpage4500.reddittv.util.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val log = LoggerConfig.createLogger("AnalyticsManager")

/**
 * the session whose `app_exit` hasn't been reported yet, as the next launch will find it.
 *
 * [startedMs] is when the current *foreground stretch* began, not when the process launched — see
 * [AnalyticsManager]. a missing [endedMs] on the next launch means the app was killed or crashed.
 */
@Serializable
private data class Session(
    val id: String,
    val startedMs: Long,
    val endedMs: Long? = null,
)

/**
 * one row of the shared `app_events` table. field names are the column names — this is posted
 * straight at PostgREST, so renaming one here renames it there.
 *
 * there is deliberately **no free-text field**: the log holds the user's locations, their weather
 * API key's length, hostnames and URLs, which is exactly why a support report is something the user
 * asks for and this isn't. a fixed set of scalar columns is what stops any of that reaching a table
 * the user never asked to send anything to.
 */
@Serializable
private data class AnalyticsEvent(
    val app: String,
    val event: String,
    @SerialName("install_id") val installId: String,
    @SerialName("session_id") val sessionId: String,
    // "android" / "ios" / "desktop" — the column that says how to read the rest of the row, and the
    // one every "mobile vs desktop" question groups by. NOT LogManager's platform string, which
    // says mac/windows/linux where this says desktop
    val platform: String,
    @SerialName("app_version") val appVersion: String,
    // OS *family name*, never a number: "Android" / "iOS" / "macOS" / "Windows" / "Linux"
    val os: String,
    // the human version: "15" / "18.2" / "26.5.1". Android's API level goes in os_api, not here
    @SerialName("os_version") val osVersion: String,
    @SerialName("os_api") val osApi: Int? = null,
    // CPU architecture only; the hardware goes in device_model
    val arch: String,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("form_factor") val formFactor: String,
    @SerialName("install_source") val installSource: String,
    val locale: String,
    // a run from Gradle or a local build rather than one that shipped; filtered out when counting
    @SerialName("is_dev") val isDev: Boolean,
    @SerialName("session_ms") val sessionMs: Long? = null,
    @SerialName("clean_exit") val cleanExit: Boolean? = null,
    // the device's clock, which can be years out — the table's own received_at is the one to trust
    @SerialName("client_ts") val clientTs: String,
)

/**
 * the server answered and said no: a batch it won't take (PGRST102), a key it doesn't like, a policy
 * that rejected the insert. another attempt sends the same bytes to the same place, so a flush stops
 * retrying when it sees this. everything else — a name that wouldn't resolve, a dead socket, a
 * timeout, a 5xx — never got a verdict, and is worth trying again.
 */
private class ServerRejected(message: String) : Exception(message)

/**
 * counts launches, so it's possible to tell how many people use the app and on what.
 *
 * see docs/Analytics.md for the table, the RLS policy and the queries. two events per session, both
 * built from a fixed set of scalar fields; nothing about the user's locations is in either.
 *
 * **a session is one process, and `app_exit` measures one foreground stretch.** the OS kills a
 * backgrounded mobile app as a matter of routine, so a desktop-style "send the exit from a shutdown
 * hook" design would report `clean_exit = false` for the normal case and measure how long the
 * process survived rather than how long anyone looked at the app. instead the exit is sent from
 * onStop / didEnterBackground, which get a normal amount of time and a working network. one
 * `app_start` per process launch, one `app_exit` per stretch — so `launches` counts processes and
 * exits count uses.
 */
object AnalyticsManager {
    private const val EVENT_START = "app_start"
    private const val EVENT_EXIT = "app_exit"

    // a permanently-offline install must not grow this without bound. 50 events is weeks of
    // ordinary use, and keeping them is what turns "usage dipped" into "the wifi was down"
    private const val MAX_PENDING = 50

    /**
     * how long a launch flush waits before its first attempt.
     *
     * this POST is the first thing any process puts on the wire — [onAppStart] runs from the
     * platform's entry point, before the weather fetch — and a device whose resolver isn't answering
     * yet (a VPN supplying DNS, opportunistic DNS-over-TLS, a radio just out of doze) fails to
     * resolve *every* host for the first second or two and then works. weather never notices,
     * because it refreshes again a minute later; this had nothing behind it, so the launch send
     * failed and rode along on the next launch's batch. stepping off t=0 is most of the fix.
     */
    private const val LAUNCH_DELAY_MS = 3_000L

    // the rest of the fix: how long to wait before each retry of a failed send. two is enough for a
    // resolver that's cold, and short enough to finish inside a session — past that the rows keep
    // until the next launch, which is the behaviour that was always here
    private val RETRY_DELAYS_MS = listOf(5_000L, 20_000L)

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // serializes the POSTs, so a flush from onAppBackground can't re-send rows a launch flush
    // already has in flight. each flush reads the pending list fresh inside the lock
    private val sendLock = Mutex()

    // the flush loop currently running, if any — see [flush]
    private var flushJob: Job? = null

    // one client for the process rather than one per POST: the shipped app sends two or three
    private val httpClient by lazy { createHttpClient(useCookies = false) }

    /**
     * the wire format, and **not** [JsonUtils.defaultJson] — `encodeDefaults = true` is what makes
     * every row carry the same keys. PostgREST rejects a bulk insert whose objects don't
     * (PGRST102, "All object keys must match"), and without this a launch row would silently drop
     * `session_ms` and `clean_exit` while the exit row beside it kept them, taking both down.
     */
    private val wireJson = Json { encodeDefaults = true }

    // this process's session id, which outlives each foreground stretch, and the start of the
    // stretch currently running — what session_ms measures
    private var sessionId: String? = null
    private var stretchStartMs = 0L

    // the automatic events only go out from the builds no store counts for us: desktop, and
    // sideloaded android from GitHub Releases. a Play Store or App Store install reports nothing —
    // Play Console's Statistics and App Store Connect's App Analytics already give installs and
    // active devices there, so a second count would duplicate a number we're handed anyway.
    private val isReportingBuild: Boolean
        get() = when (PlatformUtils.getPlatform()) {
            PlatformType.DESKTOP -> true
            PlatformType.ANDROID -> !PlatformUtils.isPlayStoreInstall()
            // iOS only ever ships through the App Store
            else -> false
        }

    private val isEnabled: Boolean
        get() = isReportingBuild && SettingsManager.getBoolean(AppPref.ANALYTICS_ENABLED)

    // the same build-time keys the support report uploads with — see composeApp/build.gradle.kts
    private val isConfigured: Boolean
        get() = LOGGING_URL.isNotEmpty() && LOGGING_API.isNotEmpty()

    /**
     * called once per process, from each platform's entry point beside [LogManager.onAppStart].
     *
     * reports the previous session's exit — either the desktop shutdown hook wrote an end time and
     * left the sending to us, or the process died with none — then starts a session of its own and
     * sends its launch event.
     *
     * the launch event goes with this run rather than the next one: somebody who opens the app once
     * and never comes back is exactly who's worth counting, and batching would lose them. it does
     * wait [LAUNCH_DELAY_MS] first, which is invisible to that count and is where the device's
     * resolver stops failing.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun onAppStart() {
        if (!isEnabled) {
            forget()
            return
        }

        val queued = loadPending().toMutableList()

        // the previous run, whether it ended properly or was killed
        loadSession()?.let { prev ->
            queued += buildEvent(
                event = EVENT_EXIT,
                sessionId = prev.id,
                sessionMs = prev.endedMs?.let { it - prev.startedMs },
                cleanExit = prev.endedMs != null,
            )
        }

        val id = Uuid.random().toString()
        sessionId = id
        stretchStartMs = DateUtils.nowMs()
        // stored so the *next* launch can report this one if the OS kills us with no exit
        saveSession(Session(id = id, startedMs = stretchStartMs))
        queued += buildEvent(event = EVENT_START, sessionId = id)

        // oldest go first when over the cap: a recent session says more about what to support
        savePending(queued.takeLast(MAX_PENDING))
        flush(LAUNCH_DELAY_MS)
    }

    /**
     * mobile only, from onStart / willEnterForeground. a new foreground stretch begins: restart the
     * clock [onAppBackground] measures, and re-arm the killed-or-crashed record.
     *
     * deliberately does not send an `app_start` — that would make `launches` count foregrounds
     * rather than processes, and a wall-mounted dashboard foregrounds constantly.
     */
    fun onAppForeground() {
        if (!isEnabled) return
        val id = sessionId ?: return
        stretchStartMs = DateUtils.nowMs()
        saveSession(Session(id = id, startedMs = stretchStartMs))
    }

    /**
     * mobile only, from onStop / didEnterBackground. the stretch the user could see just ended, so
     * this is the foreground time worth reporting — and unlike a shutdown hook, this runs with time
     * to spare and a working network, so it sends rather than only writing.
     */
    fun onAppBackground() {
        if (!isEnabled) return
        val id = sessionId ?: return

        queue(
            buildEvent(
                event = EVENT_EXIT,
                sessionId = id,
                sessionMs = DateUtils.nowMs() - stretchStartMs,
                cleanExit = true,
            )
        )
        // the exit now lives in the pending list, which survives a kill — so clear the stored
        // session, or the next launch reports this stretch a second time
        SettingsManager.setString(AppPref.ANALYTICS_SESSION, null)
        // no launch delay here: the app is leaving, and by now the resolver has been warm for a
        // while. iOS gives didEnterBackground a limited window and this has to fit inside it
        flush()
    }

    /**
     * desktop only, from the shutdown hook. writes the end time and nothing else — **no network
     * here**: a shutdown hook gets no guaranteed time budget, macOS ⌘Q is impatient, and a POST
     * would sometimes work, sometimes delay the quit and sometimes vanish. the next launch sends it.
     */
    fun onAppExit() {
        if (!isEnabled) return
        val session = loadSession() ?: return
        if (session.endedMs != null) return
        saveSession(session.copy(endedMs = DateUtils.nowMs()))
    }

    /**
     * the opt-out, from the Settings switch. turning it off leaves nothing behind rather than only
     * stopping the sending — no install id, no queued rows, no session.
     */
    fun setEnabled(enabled: Boolean) {
        log.d("setEnabled: $enabled")
        SettingsManager.setBoolean(AppPref.ANALYTICS_ENABLED, enabled)
        if (!enabled) {
            sessionId = null
            // a loop sitting in its backoff would otherwise wake up after the opt-out. forget()
            // empties the queue under it too, but stopping it is the part that reads as intended
            flushJob?.cancel()
            forget()
        }
    }

    private fun buildEvent(
        event: String,
        sessionId: String,
        sessionMs: Long? = null,
        cleanExit: Boolean? = null,
    ): AnalyticsEvent {
        val device = PlatformUtils.getDeviceInfo()
        return AnalyticsEvent(
            app = Branding.analyticsId,
            event = event,
            installId = installId(),
            sessionId = sessionId,
            platform = PlatformUtils.getPlatform().name.lowercase(),
            appVersion = APP_VERSION,
            os = device.os,
            osVersion = device.osVersion,
            osApi = device.osApi,
            arch = device.arch,
            deviceModel = device.deviceModel,
            formFactor = formFactor(),
            installSource = device.installSource,
            locale = device.locale,
            isDev = !IS_RELEASE,
            sessionMs = sessionMs,
            cleanExit = cleanExit,
            clientTs = Clock.System.now().toString(),
        )
    }

    /**
     * POST everything queued as a single JSON array, giving a failed send [RETRY_DELAYS_MS] more
     * chances before leaving the rows for the next launch (or the next background). fire and
     * forget: nothing on the launch path waits for this.
     *
     * @param initialDelayMs how long to hold the first attempt back — [LAUNCH_DELAY_MS] at launch,
     *   none once the app is running.
     */
    private fun flush(initialDelayMs: Long = 0L) {
        if (!isConfigured) {
            log.d("flush: logging URL not set")
            return
        }
        // one loop at a time. onAppBackground fires while the launch flush is still in its
        // backoff, and two loops walk the same rows through the same failures on their own
        // schedules for no gain — the running one re-reads the pending list on every attempt, so it
        // picks up whatever was queued after it started
        if (flushJob?.isActive == true) {
            //log.v { "flush: already running" }
            return
        }
        flushJob = managerScope.launch {
            if (initialDelayMs > 0) delay(initialDelayMs)
            // the waits sit *outside* the lock, so nothing else is stuck behind a backoff. each
            // attempt re-reads the pending list inside it, which is what keeps a row from going twice
            for (attempt in 0..RETRY_DELAYS_MS.size) {
                if (attempt > 0) delay(RETRY_DELAYS_MS[attempt - 1])
                // a sleeping device isn't a network to retry against — the OS is dropping our
                // packets, and it'll drop the next two the same way. leave the rows for the next
                // launch, which is a launch someone is looking at
                if (!PlatformUtils.isDeviceAwake()) {
                    //log.v { "flush: device asleep, keeping ${loadPending().size} event(s)" }
                    return@launch
                }
                val done = sendLock.withLock { attemptSend(attempt) }
                if (done) return@launch
            }
        }
    }

    /**
     * one send of whatever is queued right now. returns true when there is nothing left to try —
     * it went, there was nothing to send, or another attempt wouldn't change the answer.
     *
     * **called with [sendLock] held**, and that's load-bearing: reading the pending list, POSTing it
     * and dropping what went out are one step, or two overlapping flushes send the same rows twice.
     */
    private suspend fun attemptSend(attempt: Int): Boolean {
        val events = loadPending()
        if (events.isEmpty()) return true
        val body = runCatching { wireJson.encodeToString(events) }
            .getOrElse {
                log.e("attemptSend: encode failed: ${it.message}")
                return true
            }
        val error = post(body).exceptionOrNull()
        if (error == null) {
            // drop exactly what went out — anything queued while the POST was in flight is newer
            // and stays for the next flush
            savePending(loadPending().drop(events.size))
            //log.v("attemptSend: sent ${events.size} event(s)")
            return true
        }
        val attemptsLeft = attempt < RETRY_DELAYS_MS.size && error !is ServerRejected
        log.w("attemptSend: failed (${attempt + 1}/${RETRY_DELAYS_MS.size + 1}), keeping ${events.size} event(s): ${error.message}")
        return !attemptsLeft
    }

    private suspend fun post(body: String): Result<Unit> {
        return try {
            val url = "$LOGGING_URL/rest/v1/app_events"
            val response = httpClient.post(url) {
                headers {
                    append("apikey", LOGGING_API)
                    append("Authorization", "Bearer $LOGGING_API")
                    // required, not cosmetic: asking PostgREST to return the inserted rows needs a
                    // SELECT policy, and the table only has an insert one
                    append("Prefer", "return=minimal")
                }
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            when {
                response.status.isSuccess() -> Result.success(Unit)
                // a 5xx is the server having a bad moment and worth another attempt; a 4xx is this
                // batch being wrong, and it'll be just as wrong in five seconds
                response.status.value >= 500 ->
                    Result.failure(Exception("${response.status}: ${response.bodyAsText()}"))

                else -> Result.failure(ServerRejected("${response.status}: ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formFactor(): String = when {
        PlatformUtils.getPlatform() == PlatformType.DESKTOP -> "desktop"
        PlatformUtils.isTablet() -> "tablet"
        else -> "phone"
    }

    // made once and kept. a random UUID rather than anything derived from the device: this counts
    // installs that ran, not people, and that's the trade being made on purpose
    @OptIn(ExperimentalUuidApi::class)
    private fun installId(): String {
        SettingsManager.getString(AppPref.INSTALL_ID)?.let { return it }
        val id = Uuid.random().toString()
        SettingsManager.setString(AppPref.INSTALL_ID, id)
        return id
    }

    private fun queue(event: AnalyticsEvent) {
        savePending((loadPending() + event).takeLast(MAX_PENDING))
    }

    private fun loadSession(): Session? =
        JsonUtils.fromJson<Session>(SettingsManager.getString(AppPref.ANALYTICS_SESSION))

    private fun saveSession(session: Session) {
        SettingsManager.setString(AppPref.ANALYTICS_SESSION, JsonUtils.toJson(session))
    }

    private fun loadPending(): List<AnalyticsEvent> =
        JsonUtils.stringToList<AnalyticsEvent>(SettingsManager.getString(AppPref.ANALYTICS_PENDING))

    private fun savePending(events: List<AnalyticsEvent>) {
        SettingsManager.setString(
            AppPref.ANALYTICS_PENDING,
            if (events.isEmpty()) null else JsonUtils.toJson(events)
        )
    }

    private fun forget() {
        SettingsManager.setString(AppPref.INSTALL_ID, null)
        SettingsManager.setString(AppPref.ANALYTICS_SESSION, null)
        SettingsManager.setString(AppPref.ANALYTICS_PENDING, null)
    }
}
