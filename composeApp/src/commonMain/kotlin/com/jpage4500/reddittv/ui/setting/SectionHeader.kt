package com.jpage4500.reddittv.ui.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import reddittv.composeapp.generated.resources.*
import com.jpage4500.reddittv.ui.components.AppIcon

/**
 * section header with gray text and divider.
 *
 * deliberately small and tight: the screens that use it carry several headers each, and none of them
 * can spend a full-size heading and its whitespace on every one.
 */
@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Gray,
        modifier = Modifier.padding(vertical = 2.dp)
    )
    HorizontalDivider(color = Color.LightGray)
}

/**
 * [SectionHeader] with a chevron — tap it to show or hide [content], which slides open and shut.
 * [content] is laid out in a column spaced like the screen it sits in ([spacing])
 */
@Composable
fun CollapsibleSection(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    spacing: Int = 8,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f),
                )
                AppIcon(
                    res = if (expanded) Res.drawable.icon_chevron_down else Res.drawable.icon_chevron_right,
                    modifier = Modifier.size(18.dp),
                    color = Color.Gray,
                )
            }
            HorizontalDivider(color = Color.LightGray)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.dp), content = content)
        }
    }
}
