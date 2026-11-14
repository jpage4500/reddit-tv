package com.jpage4500.reddittv.ui.dialog.common

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.ui.components.LargeButton
import com.jpage4500.reddittv.ui.dialog.BaseDialog
import com.jpage4500.reddittv.ui.theme.Colors
import org.jetbrains.compose.resources.DrawableResource

data class LargeButtonState(
    val id: String,
    val icon: DrawableResource,
    val title: String,
    val backgroundColor: Color = Colors.PrimaryGray
)

/**
 * Reusable dialog with a title and 1-6 large buttons.
 * Buttons are arranged in a grid: up to 2 buttons per row.
 */
@Composable
fun LargeButtonDialog(
    title: String,
    buttons: List<LargeButtonState>,
    onDismiss: () -> Unit,
    onButtonSelected: (String) -> Unit = {}
) {
    // Fail early if an unsupported number of buttons is passed
    require(buttons.size in 1..6) {
        "LargeButtonDialog supports 1-6 buttons, got ${buttons.size}"
    }

    BaseDialog(
        title = title,
        horizontalAlignment = Alignment.CenterHorizontally,
        onDismiss = onDismiss,
        // we size each button to fit the available area, so no scroll is needed
        scrollable = false,
        // shrink the dialog width to the measured button grid; otherwise the dialog
        // keeps its 50%-screen width and leaves gray gutters when buttons are
        // height-constrained (e.g. 6 buttons in landscape).
        fitContentWidth = true,
    ) {
        val rows = buttons.chunked(2)
        val cols = if (buttons.size == 1) 1 else 2
        val spacing = 16.dp

        // measure the available area inside the dialog content so we can size the
        // buttons to fit. without this, LargeButton's aspectRatio(1f) makes each
        // button as tall as it is wide — fine in portrait, but in landscape the
        // 2-row stack overflows the dialog height and the content has to scroll.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val widthBased = (maxWidth - spacing * (cols - 1)) / cols
            val heightBased = (maxHeight - spacing * (rows.size - 1)) / rows.size
            // square when there's enough height (portrait, few buttons); rectangular
            // when height-constrained (4-6 buttons in landscape) so labels don't truncate.
            val buttonWidth = widthBased.coerceAtLeast(0.dp)
            val buttonHeight = minOf(widthBased, heightBased).coerceAtLeast(0.dp)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                rows.forEach { rowButtons ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally)
                    ) {
                        rowButtons.forEach { buttonState ->
                            LargeButton(
                                modifier = Modifier.size(buttonWidth, buttonHeight),
                                icon = buttonState.icon,
                                iconColor = null,
                                text = buttonState.title,
                                backgroundColor = buttonState.backgroundColor,
                                onClick = {
                                    onButtonSelected(buttonState.id)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}