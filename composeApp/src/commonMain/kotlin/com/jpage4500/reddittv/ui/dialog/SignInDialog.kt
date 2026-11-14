package com.jpage4500.reddittv.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.jpage4500.reddittv.data.SignInSite
import com.jpage4500.reddittv.ui.components.SignInBrowser
import com.jpage4500.reddittv.ui.dialog.common.ConfirmationDialog
import com.jpage4500.reddittv.ui.dialog.common.FullScreenDialog
import com.jpage4500.reddittv.ui.setting.SettingItem
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.WebSession
import reddittv.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private val log = LoggerConfig.createLogger("SignInDialog")

/**
 * [site]'s own sign-in page, full screen. closing it keeps whatever session it made —
 * [onDone] gets whether the user is signed in now
 */
@Composable
fun SignInDialog(site: SignInSite, onDone: (signedIn: Boolean) -> Unit) {
    val finish = {
        WebSession.flush()
        val signedIn = WebSession.isSignedIn(site)
        log.d("finish: ${site.label}, signed in:$signedIn")
        onDone(signedIn)
    }
    FullScreenDialog(
        title = stringResource(Res.string.sign_in_title, site.label),
        icon = Res.drawable.icon_settings,
        onDismiss = finish,
    ) {
        SignInBrowser(url = site.loginUrl, modifier = Modifier.fillMaxSize(), onDone = finish)
    }
}

/**
 * each [SignInSite] and whether the app is signed in to it: tap to sign in, or out. reached from
 * Settings
 */
@Composable
fun SignedInSitesDialog(onDismiss: () -> Unit) {
    // bumped after a sign-in or out, so the rows re-read the cookies
    var version by remember { mutableStateOf(0) }
    var signingIn by remember { mutableStateOf<SignInSite?>(null) }
    var signingOut by remember { mutableStateOf<SignInSite?>(null) }
    LaunchedEffect(Unit) {
        WebSession.refresh()
        version++
    }

    BaseDialog(
        titleRes = Res.string.signed_in_sites,
        icon = Res.drawable.icon_settings,
        onDismiss = onDismiss,
    ) {
        key(version) {
            SignInSite.entries.forEach { site ->
                val signedIn = WebSession.isSignedIn(site)
                SettingItem(
                    icon = Res.drawable.icon_play,
                    labelText = site.label,
                    valueText = stringResource(if (signedIn) Res.string.signed_in else Res.string.signed_out),
                    onClick = { if (signedIn) signingOut = site else signingIn = site },
                )
            }
        }
    }

    signingIn?.let { site ->
        SignInDialog(site = site, onDone = {
            signingIn = null
            version++
        })
    }
    signingOut?.let { site ->
        ConfirmationDialog(
            title = stringResource(Res.string.sign_out_title, site.label),
            message = stringResource(Res.string.sign_out_message, site.label),
            onDismiss = { signingOut = null },
            onConfirm = {
                WebSession.signOut(site)
                signingOut = null
                version++
            },
        )
    }
}
