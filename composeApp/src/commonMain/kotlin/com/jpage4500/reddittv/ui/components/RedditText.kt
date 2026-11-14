package com.jpage4500.reddittv.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.util.PlatformUtils

/**
 * a post's text or a comment's body — reddit markdown — with its links shown as links: a
 * "[text](url)" as just its text, and a bare https://… as itself. a tap opens the link
 */
@Composable
fun RedditText(
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(text) { redditLinks(text) }
    Text(text = annotated, fontSize = fontSize, lineHeight = lineHeight, color = color, modifier = modifier)
}

// [text](url) — the url may hold one level of parentheses, ie: a wikipedia link — or a bare url
private val LINK_REGEX = Regex("""\[([^\[\]]+)]\(((?:[^()\s]|\([^()\s]*\))+)\)|(https?://[^\s<>\[\]()]+)""")
// not part of a bare url when it ends one
private const val TRAILING_PUNCTUATION = ".,;:!?\"'*"

private val LINK_STYLES = TextLinkStyles(style = SpanStyle(color = Colors.Link, textDecoration = TextDecoration.Underline))

/** [text] with its markdown and bare links turned into clickable [LinkAnnotation]s */
fun redditLinks(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    for (match in LINK_REGEX.findAll(text)) {
        val label: String
        val url: String
        var end = match.range.last + 1
        if (match.groups[1] != null) {
            label = match.groupValues[1]
            url = absoluteUrl(match.groupValues[2])
        } else {
            var bare = match.groupValues[3]
            while (bare.isNotEmpty() && bare.last() in TRAILING_PUNCTUATION) {
                bare = bare.dropLast(1)
                end--
            }
            label = bare
            url = bare
        }
        append(text.substring(index, match.range.first))
        withLink(LinkAnnotation.Clickable(tag = url, styles = LINK_STYLES) { PlatformUtils.openUrl(url) }) {
            append(label)
        }
        index = end
    }
    if (index < text.length) append(text.substring(index))
}

/** a reddit-relative link ("/r/space", "r/space", "/u/name") made a full one */
private fun absoluteUrl(url: String): String = when {
    url.startsWith("http://") || url.startsWith("https://") -> url
    url.startsWith("/") -> "https://www.reddit.com$url"
    url.startsWith("r/") || url.startsWith("u/") -> "https://www.reddit.com/$url"
    url.startsWith("www.") -> "https://$url"
    else -> url
}
