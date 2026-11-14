package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpage4500.reddittv.util.LoggerConfig
import org.jetbrains.compose.resources.DrawableResource

private val log = LoggerConfig.createLogger("AppTextImage")

/**
 * auto size text with a faded icon in the background
 */
@Composable
fun AppTextImage(
    // image/icon
    iconUrl: String? = null,
    iconRes: DrawableResource? = null,
    iconColor: Color? = Color.White,
    // text
    text: String? = null,
    textColor: Color? = Color.White,
    maxSize: TextUnit = 72.sp,
    maxLines: Int = 1,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val hasText = !text.isNullOrEmpty()

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // -- icon --
        // when text is set the image is drawn faded, behind it
        if (iconRes != null || iconUrl != null) {
            // if text is also set, fade icon in background
            val alpha = if (hasText) 0.15f else 1.0f
            val color = if (hasText && iconColor != null) Color.White else iconColor
            AppImage(
                url = iconUrl,
                res = iconRes,
                alpha = alpha,
                color = color,
            )
        }

        // -- text --
        if (text != null) {
            AutoSizeText(
                text = text,
                maxLines = maxLines,
                maxFontSize = maxSize,
                textColor = textColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
            )
        }
    }

}