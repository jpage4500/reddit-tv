package com.jpage4500.reddittv.ui.components

import androidx.compose.runtime.Composable

/**
 * while composed, the back button or gesture runs [onBack] instead of what it would have done —
 * ie: closing the dialog this is in. Android's back button, and Escape on desktop
 */
@Composable
expect fun BackPress(onBack: () -> Unit)
