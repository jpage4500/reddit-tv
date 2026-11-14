package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpage4500.reddittv.ui.theme.Colors
import reddittv.composeapp.generated.resources.*

// how much of a logo's tile is left clear round it, as a share of the tile's height
private const val LOGO_MARGIN = 0.12f

/**
 * a video's picture, with its [length] (or LIVE) in the bottom-left corner. no picture shows the
 * play icon instead. [modifier] sets the size; [overlay] draws anything else on top; [background]
 * shows until the picture loads, or in place of one. [isLogo] for a playlist channel's: a logo is
 * any shape and often transparent, so it's shown whole with a margin rather than cropped to fill
 */
@Composable
fun VideoThumbnail(
    url: String?,
    length: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    fontSize: TextUnit = 10.sp,
    background: Color = Colors.ThumbnailBackground,
    isLogo: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background),
    ) {
        if (url != null && isLogo) {
            // a margin that keeps its proportion from a Recent row's small picture to a TV's card
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                AppImage(url = url, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(maxHeight * LOGO_MARGIN))
            }
        } else if (url != null) {
            AppImage(url = url, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            AppIcon(res = Res.drawable.icon_play, modifier = Modifier.size(iconSize).align(Alignment.Center))
        }
        if (length != null) {
            Text(
                text = length,
                color = Color.White,
                fontSize = fontSize,
                lineHeight = fontSize * 1.2f,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Colors.ThumbnailOverlay)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        overlay()
    }
}
