package com.jpage4500.reddittv.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.jpage4500.reddittv.data.*
import com.jpage4500.reddittv.util.SettingsManager
import com.jpage4500.reddittv.util.ToastManager
import reddittv.composeapp.generated.resources.*

/**
 * text sizes for the reddit screens, scaled by the user's [AppPref.TEXT_SCALE] percent (the Aa control).
 * TV sizes are the design's 1920px numbers halved (a TV is 960dp wide)
 */
class RedditType(val scale: Float, val isTv: Boolean) {
    private fun s(tv: Float, phone: Float): TextUnit = ((if (isTv) tv else phone) * scale).sp

    val tileTitle get() = s(13.5f, 16f)
    val tileMeta get() = s(10.5f, 13f)
    val postTitle get() = s(20f, 20f)
    val postBody get() = s(14f, 16f)
    val commentAuthor get() = s(11.5f, 13f)
    val commentBody get() = s(13f, 15f)
    val header get() = s(20f, 20f)
    val sidebarItem get() = s(14f, 16f)
    val sidebarSection get() = (if (isTv) 10.5f else 12f).sp

    // the subreddit / score / comments / age line under a post: not scaled, so it always fits the tile
    val metaRow get() = (if (isTv) 10.5f else 13f).sp
    val metaRowLineHeight get() = metaRow * 1.25f
}

val LocalRedditType = staticCompositionLocalOf { RedditType(1f, isTv = false) }

/** provide [LocalRedditType] from the saved [AppPref.TEXT_SIZE]; it follows changes live */
@Composable
fun ProvideRedditType(isTv: Boolean, content: @Composable () -> Unit) {
    val percent by SettingsManager.getIntAsState(AppPref.TEXT_SCALE)
    val type = remember(percent, isTv) { RedditType(percent / 100f, isTv) }
    CompositionLocalProvider(LocalRedditType provides type, content = content)
}

/** step the saved text size up or down one of [TEXT_SCALES] */
fun changeTextSize(bigger: Boolean) {
    val current = SettingsManager.getInt(AppPref.TEXT_SCALE)
    val next = if (bigger) TEXT_SCALES.firstOrNull { it > current } else TEXT_SCALES.lastOrNull { it < current }
    if (next != null) SettingsManager.setInt(AppPref.TEXT_SCALE, next)
    // say where it landed — also at either end, where the press changes nothing
    ToastManager.showToast(Res.string.text_size_toast, next ?: current)
}
