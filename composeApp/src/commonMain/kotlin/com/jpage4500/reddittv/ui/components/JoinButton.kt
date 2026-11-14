package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.ui.theme.LocalRedditType
import org.jetbrains.compose.resources.*
import reddittv.composeapp.generated.resources.*

/**
 * a community's Join button (feed and post headers): [isJoined] says which way it goes, [isPending] while reddit is
 * asked (the button does nothing then)
 */
data class JoinState(
    val isJoined: Boolean,
    val isPending: Boolean,
    val onClick: () -> Unit,
)

/** "Join" (filled) or "Leave" (outlined), at the right of a community feed's or a post's header */
@Composable
fun JoinButton(join: JoinState, modifier: Modifier = Modifier) {
    val type = LocalRedditType.current
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val label = if (join.isJoined) Res.string.feed_leave else Res.string.feed_join
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> Colors.CardFocused
                    join.isJoined -> Colors.Card
                    else -> Colors.Accent
                }
            )
            .then(if (join.isJoined && !isFocused) Modifier.border(1.dp, Colors.CardBorder, CircleShape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = !join.isPending, onClick = join.onClick)
            .heightIn(min = 30.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (join.isPending) {
            CircularProgressIndicator(
                color = if (isFocused || !join.isJoined) Colors.TextOnFocused else Colors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Text(
                text = stringResource(label),
                fontSize = type.tileMeta,
                fontWeight = FontWeight.SemiBold,
                color = if (isFocused || !join.isJoined) Colors.TextOnFocused else Colors.Text,
            )
        }
    }
}
