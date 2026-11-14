package com.jpage4500.reddittv.ui.dialog.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import com.jpage4500.reddittv.ui.theme.Dimens
import com.jpage4500.reddittv.util.PlatformUtils

@Composable
fun FormattedText(
    text: String,
    textSize: TextUnit = Dimens.DIALOG_TEXT_SIZE,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    val annotatedMessage = remember(text) {
        buildAnnotatedString {
            // matches either <https://google.com|Google> or a bare https://google.com/page/1
            val linkRegex = "<([^>|]+)\\|([^>]+)>|(https?://[^\\s<>]+)".toRegex()
            var currentIndex = 0

            for (match in linkRegex.findAll(text)) {
                append(text.substring(currentIndex, match.range.first))

                val url: String
                val linkText: String
                val matchEnd: Int
                if (match.groupValues[1].isNotEmpty()) {
                    url = match.groupValues[1]
                    linkText = match.groupValues[2]
                    matchEnd = match.range.last + 1
                } else {
                    // bare URL — strip common trailing punctuation that isn't part of the URL
                    var bare = match.groupValues[3]
                    val trailing = ".,;:!?)]}>\"'"
                    var trimmed = 0
                    while (bare.isNotEmpty() && bare.last() in trailing) {
                        bare = bare.dropLast(1)
                        trimmed++
                    }
                    url = bare
                    linkText = bare
                    matchEnd = match.range.last + 1 - trimmed
                }

                val link = LinkAnnotation.Clickable(
                    tag = url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = Color.Blue,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                ) {
                    PlatformUtils.openUrl(url)
                }

                withLink(link) {
                    append(linkText)
                }

                currentIndex = matchEnd
            }

            if (currentIndex < text.length) {
                append(text.substring(currentIndex))
            }
        }
    }

    // Standard Text component natively handles LinkAnnotations in Compose 1.7.0+
    Text(
        text = annotatedMessage,
        modifier = modifier,
        fontSize = textSize,
        textAlign = textAlign,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}