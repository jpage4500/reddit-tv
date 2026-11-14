package com.jpage4500.reddittv.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpage4500.reddittv.data.Branding
import com.jpage4500.reddittv.data.SignInSite
import com.jpage4500.reddittv.manager.PhoneSignInServer
import com.jpage4500.reddittv.manager.loginAutofillScript
import com.jpage4500.reddittv.ui.components.BackPress
import com.jpage4500.reddittv.ui.components.SignInBrowser
import com.jpage4500.reddittv.ui.components.focusRing
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.PlatformType
import com.jpage4500.reddittv.util.PlatformUtils
import com.jpage4500.reddittv.util.WebSession
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.*
import reddittv.composeapp.generated.resources.*

private val log = LoggerConfig.createLogger("SignInScreen")

// how long a phone's autofill gets to sign in, once the page has loaded, before the page is shown
// for the user to finish (2FA, a captcha, a wrong password)
private const val AUTOFILL_REVEAL_MS = 20_000L

/**
 * sign in (design board 1). TV: a QR code to a page the TV serves itself ([PhoneSignInServer]);
 * what the phone sends is typed into reddit's own login page here. the TV can also sign in with
 * the remote, and anyone can browse signed out. phone: reddit's login page in the app's browser.
 *
 * [onDone] gets whether there's a session now
 */
@Composable
fun SignInScreen(isTv: Boolean, onDone: (signedIn: Boolean) -> Unit) {
    // the login page, visible: after a phone sent credentials (with the autofill), or by hand
    var browserScript by remember { mutableStateOf<String?>(null) }
    var showBrowser by remember { mutableStateOf(false) }
    // the username a phone sent, shown while it signs in
    var signingInAs by remember { mutableStateOf<String?>(null) }
    val finish = {
        WebSession.flush()
        val signedIn = WebSession.isSignedIn(SignInSite.REDDIT)
        log.d("finish: signed in:$signedIn")
        onDone(signedIn)
    }

    if (isTv) {
        DisposableEffect(Unit) {
            PhoneSignInServer.start()
            onDispose { PhoneSignInServer.stop() }
        }
        val creds by PhoneSignInServer.credentials.collectAsState()
        LaunchedEffect(creds) {
            val c = PhoneSignInServer.consume() ?: return@LaunchedEffect
            browserScript = loginAutofillScript(c)
            signingInAs = c.username
            showBrowser = true
        }
    }

    if (showBrowser && WebSession.isSupported) {
        BackPress {
            showBrowser = false
            browserScript = null
            signingInAs = null
            if (!isTv) onDone(WebSession.isSignedIn(SignInSite.REDDIT))
        }
        // reddit's white login page stays covered: while it loads, and while the autofill signs in.
        // an autofill that hasn't finished by AUTOFILL_REVEAL_MS needs the user (2FA, a captcha, a
        // wrong password), so the page shows then
        val isAutofill = browserScript != null
        var isPageLoaded by remember { mutableStateOf(false) }
        var showPage by remember { mutableStateOf(false) }
        LaunchedEffect(isPageLoaded, isAutofill) {
            if (!isPageLoaded) return@LaunchedEffect
            if (isAutofill) delay(AUTOFILL_REVEAL_MS)
            log.d("SignInScreen: showing the page, autofill:$isAutofill")
            showPage = true
        }
        Box(Modifier.fillMaxSize().background(Colors.Background)) {
            SignInBrowser(
                url = SignInSite.REDDIT.loginUrl,
                script = browserScript,
                modifier = Modifier.fillMaxSize(),
                onDone = finish,
                onPageLoaded = { isPageLoaded = true },
            )
            AnimatedVisibility(visible = !showPage, enter = fadeIn(), exit = fadeOut(tween(500))) {
                SignInProgress(username = signingInAs)
            }
            if (showPage && isAutofill) {
                Text(
                    stringResource(Res.string.sign_in_finish_here),
                    color = Colors.Text,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Colors.Card)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
        return
    }

    if (isTv) TvSignIn(onSignInHere = { showBrowser = true }, onSkip = { onDone(false) })
    else PhoneSignIn(onSignIn = { showBrowser = true }, onSkip = { onDone(false) })
}

@Composable
private fun TvSignIn(onSignInHere: () -> Unit, onSkip: () -> Unit) {
    val url by PhoneSignInServer.url.collectAsState()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Colors.Background)) {
    LogoBackdrop()
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Logo()
            Spacer(Modifier.height(28.dp))
            Text(stringResource(Res.string.sign_in_with_phone), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Colors.Text)
            Spacer(Modifier.height(20.dp))
            Step(1, stringResource(Res.string.sign_in_step_scan))
            Step(2, stringResource(Res.string.sign_in_step_type))
            Step(3, stringResource(Res.string.sign_in_step_2fa))
            Spacer(Modifier.height(20.dp))
            Text(stringResource(Res.string.sign_in_same_wifi), fontSize = 13.sp, color = Colors.TextDim)
            url?.let {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(Res.string.sign_in_no_camera), fontSize = 13.sp, color = Colors.TextDim)
                Text(it.removePrefix("http://"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Colors.Text)
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(stringResource(Res.string.sign_in_on_tv), onSignInHere, Modifier.focusRequester(firstFocus))
                TvButton(stringResource(Res.string.browse_signed_out), onSkip)
            }
        }
        Spacer(Modifier.width(48.dp))
        Box(
            modifier = Modifier.size(260.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            val qrUrl = url
            if (qrUrl != null) {
                Image(painter = rememberQrCodePainter(qrUrl), contentDescription = qrUrl, modifier = Modifier.fillMaxSize())
            } else {
                Text(stringResource(Res.string.sign_in_needs_wifi), color = Colors.TextOnFocused, fontSize = 14.sp)
            }
        }
    }
    }
}

/** a TV button that's accent-filled only while the remote is on it, so focus is obvious */
@Composable
private fun TvButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    Button(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interaction,
        border = if (isFocused) null else BorderStroke(1.dp, Colors.CardBorder),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) Colors.Accent else Colors.Card,
            contentColor = if (isFocused) Colors.TextOnFocused else Colors.Text,
        ),
    ) { Text(text, fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun PhoneSignIn(onSignIn: () -> Unit, onSkip: () -> Unit) {
    // a keyboard (desktop) starts on Sign in, so Enter, Tab and the arrows work straight away.
    // not on a phone: touch never shows focus, and the first key press finds it anyway
    val firstFocus = remember { FocusRequester() }
    if (PlatformUtils.getPlatform() == PlatformType.DESKTOP) {
        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    }
    Box(Modifier.fillMaxSize().background(Colors.Background)) {
    LogoBackdrop()
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Logo()
        Spacer(Modifier.height(24.dp))
        Text(stringResource(Res.string.phone_tagline), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Colors.Text)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(Res.string.phone_sign_in_desc, Branding.appName),
            fontSize = 15.sp,
            color = Colors.TextDim,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth().height(52.dp).focusRing(CircleShape).focusRequester(firstFocus),
            colors = ButtonDefaults.buttonColors(containerColor = Colors.Accent, contentColor = Colors.TextOnFocused),
        ) { Text(stringResource(Res.string.sign_in_to_reddit), fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().height(52.dp).focusRing(CircleShape)) {
            Text(stringResource(Res.string.browse_signed_out), color = Colors.Text)
        }
    }
    }
}

/** the mark, very large and faded, behind a sign-in screen's content (or an empty, loading feed) */
@Composable
fun LogoBackdrop() {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        // bigger than the screen, bleeding off its right edge
        Image(
            painter = painterResource(Branding.mark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alpha = 0.06f,
            modifier = Modifier.requiredSize(maxOf(maxWidth, maxHeight) * 0.9f).offset(x = maxWidth * 0.2f),
        )
    }
}

/**
 * covers reddit's login page while it loads and signs in: the logo breathing, the user it's
 * signing in as ([username], from the phone) and a moving bar
 */
@Composable
private fun SignInProgress(username: String?) {
    val pulse = rememberInfiniteTransition(label = "signInPulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val glow by pulse.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    Column(
        modifier = Modifier.fillMaxSize().background(Colors.Background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // a soft halo behind the mark, breathing with it
            Box(
                Modifier.size(132.dp).graphicsLayer { scaleX = scale * 1.1f; scaleY = scale * 1.1f }
                    .clip(CircleShape).background(Colors.Accent.copy(alpha = glow)),
            )
            Box(
                modifier = Modifier.size(88.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(24.dp)).background(Colors.Accent),
                contentAlignment = Alignment.Center,
            ) { Text("r", fontSize = 54.sp, fontWeight = FontWeight.Bold, color = Colors.TextOnFocused) }
        }
        Spacer(Modifier.height(36.dp))
        Text(
            if (username != null) stringResource(Res.string.signing_in_as, username) else stringResource(Res.string.opening_sign_in),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Colors.Text,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.signing_in_desc), fontSize = 15.sp, color = Colors.TextDim)
        Spacer(Modifier.height(28.dp))
        LinearProgressIndicator(
            modifier = Modifier.width(240.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = Colors.Accent,
            trackColor = Colors.Card,
        )
    }
}

@Composable
private fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Colors.Accent),
            contentAlignment = Alignment.Center,
        ) { Text("r", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Colors.TextOnFocused) }
        Spacer(Modifier.width(12.dp))
        Text(Branding.appName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Colors.Text)
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(Colors.Card),
            contentAlignment = Alignment.Center,
        ) { Text("$n", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Colors.Accent) }
        Spacer(Modifier.width(14.dp))
        Text(text, fontSize = 16.sp, color = Colors.Text)
    }
}
