package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.Utils

private val log = LoggerConfig.createLogger("AutoSizeText")

/**
 * auto-sizing text that scales to fit available space
 * similar to Android's AppCompatTextView with autoSizeTextType="uniform"
 */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color? = Color.White,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 1,
    maxFontSize: TextUnit = 72.sp,
    minFontSize: TextUnit = 16.sp,
    style: TextStyle = LocalTextStyle.current,
    onSizeChange: ((TextUnit) -> Unit)? = null,
) {
    val color = textColor ?: Color.White
    // calculate shadow based on text color (similar to Java code)
    //val showTextDropShadow = color == Color.White // && color != Color.Unspecified
    val shadowColor = if (Utils.isColorDark(color)) Color.White else Color.Black
    val shadow = Shadow(
        color = shadowColor,
        offset = Offset(1f, 1f),
        blurRadius = if (shadowColor == Color.Black) 5f else 1f
    )

    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            fontSize = minFontSize,
            lineHeight = TextUnit.Unspecified,
            textAlign = textAlign,
            shadow = shadow
        ),
        color = { color },
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(
            maxFontSize = maxFontSize,
            minFontSize = minFontSize,
        ),
        onTextLayout = if (onSizeChange != null) {
            { result: TextLayoutResult ->
                onSizeChange(result.layoutInput.style.fontSize)
            }
        } else null,
    )
}



