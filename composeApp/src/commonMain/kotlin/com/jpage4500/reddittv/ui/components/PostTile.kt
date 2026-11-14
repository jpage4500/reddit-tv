package com.jpage4500.reddittv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.data.Post
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.ui.theme.LocalRedditType
import com.jpage4500.reddittv.util.RedditFormat

/**
 * a post in the TV grid (design board 2): media on top, a 2-line title, then subreddit, score and
 * comments. the focused tile turns light and grows, and a video in it plays, muted.
 *
 * the arrows always move through the grid: a gallery shows its first image here, and is paged
 * through on the post screen
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostTile(
    post: Post,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val type = LocalRedditType.current
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f)

    val shape = RoundedCornerShape(10.dp)
    Column(
        // the focus target sits outside the zoom: inside it, the grid's bring-into-view saw the
        // growing tile and nudged the grid on every LEFT/RIGHT
        modifier = modifier
            // the remote's OK: a press opens the post, holding it opens the post's options
            .okKeyPress(onPress = onClick, onLongPress = { onLongClick?.invoke() })
            .combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick, onClick = onClick)
            .height(192.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (isFocused) Modifier.shadow(16.dp, shape) else Modifier)
            .clip(shape)
            .background(if (isFocused) Colors.CardFocused else Colors.Card),
    ) {
        AutoplayThumbnail(
            post = post,
            isActive = isFocused,
            modifier = Modifier.fillMaxWidth().height(115.dp),
            textColor = if (isFocused) Colors.TextDimOnFocused else Colors.TextDim,
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 8.dp),
        ) {
            // the title takes what the meta line leaves: at a big text size it loses its 2nd line,
            // the meta line is never pushed out of the tile
            Text(
                text = post.title,
                modifier = Modifier.weight(1f),
                fontSize = type.tileTitle,
                lineHeight = type.tileTitle * 1.3f,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isFocused) Colors.TextOnFocused else Colors.Text,
            )
            PostMeta(
                post,
                color = if (isFocused) Colors.TextDimOnFocused else Colors.TextDim,
                pillColor = if (isFocused) Colors.MetaPillOnFocused else Colors.MetaPill,
            )
        }
    }
}

/**
 * "r/space  [▲ 12.3k]  [💬 456]  4h": the score and comments sit in pills, so nothing between them.
 * a long subreddit is cut from the front, keeping the end of its name
 */
@Composable
fun PostMeta(
    post: Post,
    color: Color,
    showSubreddit: Boolean = true,
    pillColor: Color = Colors.MetaPill,
) {
    val type = LocalRedditType.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showSubreddit) {
            Text(
                text = "r/${post.subreddit}",
                fontSize = type.metaRow,
                lineHeight = type.metaRowLineHeight,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                // takes what's left after the pills, never more than it needs
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        // signed out (RSS) there are no counts to show
        if (post.score != Post.UNKNOWN_COUNT) {
            MetaPill(
                icon = MetaIcons.Upvote,
                text = RedditFormat.count(post.score),
                color = when (post.likes) {
                    true -> Colors.Accent
                    false -> Colors.Downvote
                    null -> color
                },
                background = pillColor,
            )
        }
        if (post.numComments != Post.UNKNOWN_COUNT) MetaPill(MetaIcons.Comment, RedditFormat.count(post.numComments), color, pillColor)
        Text(RedditFormat.age(post.createdUtc), fontSize = type.metaRow, lineHeight = type.metaRowLineHeight, color = color, maxLines = 1)
    }
}

/** an icon and a count in a rounded pill */
@Composable
private fun MetaPill(icon: ImageVector, text: String, color: Color, background: Color) {
    val type = LocalRedditType.current
    val iconSize = with(LocalDensity.current) { (type.metaRow * 1.15f).toDp() }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = type.metaRow, lineHeight = type.metaRowLineHeight, color = color, maxLines = 1, softWrap = false)
    }
}
