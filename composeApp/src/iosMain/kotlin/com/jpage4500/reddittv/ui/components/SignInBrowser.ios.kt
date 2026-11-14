package com.jpage4500.reddittv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// never shown: WebSession.isSupported is false here
@Composable
actual fun SignInBrowser(url: String, modifier: Modifier, script: String?, onDone: () -> Unit, onPageLoaded: () -> Unit) {}
