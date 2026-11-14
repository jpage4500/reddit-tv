package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpage4500.reddittv.ui.theme.Colors
import reddittv.composeapp.generated.resources.Res
import reddittv.composeapp.generated.resources.icon_close
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.*
import reddittv.composeapp.generated.resources.*

/**
 * reusable dialog title component with optional icon, title text, and close button. the title
 * never wraps: a long one ellipsizes
 *
 * @param title the title text to display
 * @param icon optional icon to show on the left side of the title
 * @param onDismiss callback when close button is clicked
 */
@Composable
fun DialogTitle(
    title: String,
    icon: DrawableResource? = null,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // tap anywhere to close. the row itself isn't a stop for a remote — the X below is,
            // reached by pressing UP from the top of the dialog
            .focusProperties { canFocus = false }
            .clickable(onClick = onDismiss)
            .background(Colors.DIALOG_TITLE)
            .padding(16.dp, 6.dp, 12.dp, 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // left side: icon + title
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // optional icon
            icon?.let {
                Image(
                    painter = painterResource(it),
                    contentDescription = title,
                    modifier = Modifier.size(32.dp),
                    colorFilter = ColorFilter.tint(Colors.DialogTitleText)
                )
            }

            // title text
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Colors.DialogTitleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // close button on the right — focusable, so a remote can close the dialog too
        Image(
            painter = painterResource(Res.drawable.icon_close),
            contentDescription = stringResource(Res.string.close),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .focusBorder(RoundedCornerShape(8.dp))
                .clickable(onClick = onDismiss)
                .padding(4.dp)
                .size(32.dp),
            colorFilter = ColorFilter.tint(Colors.DialogTitleText)
        )
    }
}
