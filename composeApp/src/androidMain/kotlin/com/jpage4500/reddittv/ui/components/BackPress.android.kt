package com.jpage4500.reddittv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun BackPress(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
}
