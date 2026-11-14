package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import reddittv.composeapp.generated.resources.*
import com.jpage4500.reddittv.data.SignInSite
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.WebSession
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

private val log = LoggerConfig.createLogger("SignInBrowser")

/**
 * the sign-in page opens in a window of the user's Chrome or Edge, on the app's own profile — this
 * just says so. [onDone] once they've signed in (the window closes itself) or closed the window;
 * leaving the dialog first closes the window too
 */
@Composable
actual fun SignInBrowser(url: String, modifier: Modifier, script: String?, onDone: () -> Unit, onPageLoaded: () -> Unit) {
    val site = remember(url) { SignInSite.fromUrl(url) }
    // bumped by Try Again, to open the window again
    var attempt by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(url, attempt) {
        if (site == null) {
            failed = true
            return@LaunchedEffect
        }
        failed = false
        try {
            WebSession.signIn(site, url)
            currentOnDone()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e("can't open the window: ${e.message}")
            failed = true
        }
    }

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val text = if (failed) {
            stringResource(Res.string.sign_in_window_failed)
        } else {
            stringResource(Res.string.sign_in_window, site?.label.orEmpty())
        }
        Text(text = text, color = Colors.DarkGray, textAlign = TextAlign.Center)
        if (failed) {
            Spacer(modifier = Modifier.height(12.dp))
            AppButton(text = stringResource(Res.string.update_retry), onClick = { attempt++ })
        }
    }
}
