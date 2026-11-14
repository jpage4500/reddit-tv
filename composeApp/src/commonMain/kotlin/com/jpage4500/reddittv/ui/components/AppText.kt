package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.Utils.isColorDark

private val log = LoggerConfig.createLogger("AppText")

/**
 * text that has optional drop-shadow to make more readable
 */
@Composable
fun AppText(
    text: String,
    showShadow: Boolean = true,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
) {
    // calculate shadow based on text color (similar to Java code)
    val shadowColor = if (isColorDark(textColor)) Color.White else Color.Black
    val shadow = if (showShadow) {
        Shadow(
            color = shadowColor,
            offset = Offset(1f, 1f),
            blurRadius = 5f
        )
    } else {
        null
    }

    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            fontSize = fontSize,
            textAlign = textAlign,
            shadow = shadow
        ),
        color = { textColor },
        maxLines = maxLines,
    )
}



