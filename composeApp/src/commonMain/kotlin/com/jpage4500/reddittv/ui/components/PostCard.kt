package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.data.Post
import com.jpage4500.reddittv.data.PostMedia
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.util.RedditFormat
import com.jpage4500.reddittv.ui.theme.LocalRedditType

/**
 * a post in the phone list (design board 5): subreddit and age, title, then media sized so about
 * two media posts fit a screen. a gallery swipes sideways. a video plays, muted, while it's the
 * list's main post ([isActive])
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    post: Post,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val type = LocalRedditType.current
    Column(modifier = modifier.fillMaxWidth().focusRing(RectangleShape).combinedClickable(onLongClick = onLongClick, onClick = onClick).padding(vertical = 10.dp)) {
        Text(
            text = "r/${post.subreddit}",
            fontSize = type.tileMeta,
            fontWeight = FontWeight.SemiBold,
            color = Colors.TextDim,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = post.title,
            fontSize = type.tileTitle,
            lineHeight = type.tileTitle * 1.3f,
            fontWeight = FontWeight.SemiBold,
            color = Colors.Text,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        when (val media = post.media) {
            PostMedia.None -> if (post.selfText.isNotBlank()) {
                Text(
                    text = RedditFormat.oneLine(post.selfText),
                    fontSize = type.tileMeta,
                    lineHeight = type.tileMeta * 1.3f,
                    color = Colors.TextDim,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            is PostMedia.Gallery -> {
                val pager = rememberPagerState { media.images.size }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().aspectRatio(cardAspect(media))) { page ->
                    PostThumbnail(post, Modifier.fillMaxSize(), galleryIndex = page)
                }
            }
            // a link with no picture (reddit gave none and its page didn't unfurl to one) gets a
            // slim row with its site, not an empty media box
            is PostMedia.Link -> if (rememberMediaImage(media) != null) {
                AutoplayThumbnail(post, isActive, Modifier.fillMaxWidth().aspectRatio(cardAspect(media)))
            } else {
                LinkRow(post.domain)
            }
            else -> AutoplayThumbnail(post, isActive, Modifier.fillMaxWidth().aspectRatio(cardAspect(media)))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { PostMeta(post, Colors.TextDim, showSubreddit = false) }
    }
    HorizontalDivider(color = Colors.CardBorder)
}

/** a picture-less link post's site: the "opens elsewhere" arrow and its domain */
@Composable
private fun LinkRow(domain: String) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(imageVector = MetaIcons.ExternalLink, contentDescription = null, tint = Colors.TextDim, modifier = Modifier.size(16.dp))
        Text(text = domain, fontSize = LocalRedditType.current.tileMeta, color = Colors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** tall images are cut to 4:5 so a screen still holds about two posts */
private fun cardAspect(media: PostMedia): Float = (media.aspectRatio ?: (16f / 9f)).coerceIn(0.8f, 2f)
