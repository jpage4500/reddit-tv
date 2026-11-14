package com.jpage4500.reddittv.manager

import com.jpage4500.reddittv.data.Branding
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.createNetworkMonitor
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random
import org.jetbrains.compose.resources.getString
import reddittv.composeapp.generated.resources.*

private val log = LoggerConfig.createLogger("PhoneSignInServer")

/**
 * sign in from a phone without hosting anything: the TV serves a small sign-in page on the LAN and
 * shows its address as a QR code. the phone's camera opens it, the user types their reddit name and
 * password there (a phone keyboard instead of a remote), and the page posts them back to the TV —
 * which types them into reddit's own login page in its WebView (see [loginAutofillScript]). the
 * session that page makes is the app's session; nothing is stored here.
 *
 * the URL carries a random token, so only someone who can see the TV's screen can post to it. the
 * page is plain HTTP on the LAN — the same trust as typing on the TV with the remote — and the
 * credentials are held in memory only until the WebView has used them.
 */
object PhoneSignInServer {
    private const val PORT = 8765

    data class Credentials(val username: String, val password: String)

    private var server: EmbeddedServer<*, *>? = null
    private var token: String = ""

    private val _credentials = MutableStateFlow<Credentials?>(null)

    /** set once a phone posts the form; [consume] clears it */
    val credentials: StateFlow<Credentials?> = _credentials.asStateFlow()

    private val _url = MutableStateFlow<String?>(null)

    /** the address for the QR code, or null when not listening (no LAN address, port taken) */
    val url: StateFlow<String?> = _url.asStateFlow()

    fun start() {
        if (server != null) return
        val ip = createNetworkMonitor().getLocalIpAddress()
        if (ip == null) {
            log.e("start: no LAN address")
            return
        }
        token = Random.nextLong().toULong().toString(36)
        try {
            server = embeddedServer(CIO, port = PORT, host = "0.0.0.0") {
                routing {
                    get("/s/{token}") {
                        if (call.parameters["token"] != token) return@get call.respond(HttpStatusCode.NotFound)
                        call.respondText(formPage(error = null), ContentType.Text.Html)
                    }
                    post("/s/{token}") {
                        if (call.parameters["token"] != token) return@post call.respond(HttpStatusCode.NotFound)
                        val form = call.receiveParameters()
                        val username = form["username"]?.trim().orEmpty()
                        val password = form["password"].orEmpty()
                        if (username.isEmpty() || password.isEmpty()) {
                            call.respondText(formPage(error = getString(Res.string.web_missing_fields)), ContentType.Text.Html)
                            return@post
                        }
                        // inside a route, `log` is ktor's application logger
                        onCredentials(Credentials(username, password))
                        call.respondText(donePage(), ContentType.Text.Html)
                    }
                }
            }.start(wait = false)
            _url.value = "http://$ip:$PORT/s/$token"
            log.d("start: listening on $ip:$PORT")
        } catch (e: Exception) {
            log.e("start: ${e.message}")
            server = null
        }
    }

    private fun onCredentials(creds: Credentials) {
        log.d("onCredentials: received for ${creds.username.take(2)}…")
        _credentials.value = creds
    }

    fun stop() {
        server?.stop(500, 1000)
        server = null
        _url.value = null
        _credentials.value = null
        log.d("stop")
    }

    /** take the posted credentials, once */
    fun consume(): Credentials? = _credentials.value.also { _credentials.value = null }

    private suspend fun formPage(error: String?): String = page(
        """
        <h1>${getString(Res.string.sign_in_to_reddit)}</h1>
        <p class="dim">${getString(Res.string.web_sign_in_desc, Branding.appName)}</p>
        ${if (error != null) "<p class=\"err\">$error</p>" else ""}
        <form method="post">
          <label>${getString(Res.string.web_username)}<input name="username" autocomplete="username" autocapitalize="none" required></label>
          <label>${getString(Res.string.web_password)}<input name="password" type="password" autocomplete="current-password" required></label>
          <label class="show"><input type="checkbox" onchange="document.querySelector('input[name=password]').type=this.checked?'text':'password'">${getString(Res.string.web_show_password)}</label>
          <button type="submit">${getString(Res.string.web_submit)}</button>
        </form>
        """
    )

    private suspend fun donePage(): String = page(
        """
        <h1>${getString(Res.string.web_sent_title)}</h1>
        <p class="dim">${getString(Res.string.web_sent_desc)}</p>
        """
    )

    private suspend fun page(body: String): String = """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>${getString(Res.string.web_page_title, Branding.appName)}</title>
        <style>
          body{margin:0;background:#0B0C0E;color:#F4F5F7;font:16px/1.5 -apple-system,system-ui,sans-serif;padding:32px 20px}
          h1{font-size:24px;margin:0 0 8px}.dim{color:#AEB4BF}.err{color:#FF8A80}
          label{display:block;margin:18px 0 0;font-weight:600}
          input{display:block;box-sizing:border-box;width:100%;margin-top:6px;padding:14px;border-radius:12px;border:1px solid #26292F;background:#17191D;color:#F4F5F7;font-size:17px}
          label.show{display:flex;align-items:center;gap:10px;margin-top:12px;font-weight:400;color:#AEB4BF}
          label.show input{display:inline;width:20px;height:20px;margin:0;padding:0;accent-color:#FFB84D}
          button{margin-top:28px;width:100%;padding:16px;border:0;border-radius:999px;background:#FFB84D;color:#0B0C0E;font-size:17px;font-weight:700}
        </style></head><body>$body</body></html>
    """.trimIndent()
}

/**
 * JavaScript run in the sign-in WebView on reddit's login page: fills [creds] in and submits.
 * reddit's login form lives inside web components (faceplate-*), so inputs are found by walking
 * shadow roots. it retries for a few seconds while the page builds itself, and does nothing on a
 * page without a password field — the user can always finish by hand
 */
fun loginAutofillScript(creds: PhoneSignInServer.Credentials): String {
    fun js(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    return """
    (function(){
      function all(root, sel, out){
        out = out || [];
        root.querySelectorAll(sel).forEach(function(e){ out.push(e); });
        root.querySelectorAll('*').forEach(function(e){ if (e.shadowRoot) all(e.shadowRoot, sel, out); });
        return out;
      }
      function set(input, value){
        var proto = Object.getPrototypeOf(input);
        var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
        setter.call(input, value);
        input.dispatchEvent(new Event('input', {bubbles:true, composed:true}));
        input.dispatchEvent(new Event('change', {bubbles:true, composed:true}));
      }
      var tries = 0;
      function attempt(){
        var user = all(document, 'input[name="username"]')[0];
        var pass = all(document, 'input[name="password"]')[0];
        if (!user || !pass) { if (tries++ < 20) setTimeout(attempt, 500); return; }
        set(user, ${js(creds.username)});
        set(pass, ${js(creds.password)});
        setTimeout(function(){
          var btn = all(document, 'button').filter(function(b){ return /log ?in/i.test(b.textContent || ''); })[0];
          if (btn) btn.click(); else pass.form && pass.form.requestSubmit && pass.form.requestSubmit();
        }, 400);
      }
      attempt();
    })();
    """.trimIndent()
}
