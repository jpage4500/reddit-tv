package com.jpage4500.reddittv.ui.setting

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.ui.dialog.BaseDialog
import com.jpage4500.reddittv.ui.theme.Colors
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * a choice setting up close: what it's for, a slider across its choices, and what the selected one
 * does. a change applies as it's made, so there's no OK: back or the close (x) closes it.
 * opened from a row inside another dialog, it has to be called from outside that dialog's content,
 * which leaves the composition while this one is on top
 * @param choices each choice's short name: the slider's stops
 * @param descriptions what each choice does, shown under the slider for the selected one
 * @param infoText what the setting is for, at the top of the dialog
 */
@Composable
fun SettingChoiceDialog(
    icon: DrawableResource,
    labelRes: StringResource,
    choices: List<String>,
    descriptions: List<String>,
    infoText: String,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BaseDialog(
        titleRes = labelRes,
        icon = icon,
        onDismiss = onDismiss,
    ) {
        Text(
            text = infoText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        ChoiceSlider(
            choices = choices,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = descriptions[selectedIndex],
            style = MaterialTheme.typography.bodyMedium,
            // a description one line longer than the last shouldn't shift the buttons under it
            minLines = 3,
        )
    }
}

/**
 * a few fixed choices as stops along a track: a slider a remote can drive. one focus stop —
 * Left/Right move between the stops; by touch, a tap on a stop picks it
 */
@Composable
fun ChoiceSlider(
    choices: List<String>,
    selectedIndex: Int,
    primaryColor: Color = Colors.PrimaryBlue,
    onSelect: (Int) -> Unit,
) {
    val lastIndex = choices.lastIndex
    var isFocused by remember { mutableStateOf(false) }
    // a remote shows where it is by the selected stop's ring
    val ringColor = if (isFocused) Colors.ChoiceFocus else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // ahead of the focus target, so it sees the keys first: Left/Right would otherwise move
            // focus out of the slider
            .onPreviewKeyEvent { event ->
                val step = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) {
                    val next = (selectedIndex + step).coerceIn(0, lastIndex)
                    if (next != selectedIndex) onSelect(next)
                }
                true
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .pointerInput(choices.size) {
                detectTapGestures { offset ->
                    onSelect((offset.x / size.width * choices.size).toInt().coerceIn(0, lastIndex))
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .drawBehind {
                    // each stop sits in the middle of its name's column
                    val cell = size.width / choices.size
                    val y = size.height / 2
                    val first = Offset(cell / 2, y)
                    val selected = Offset(cell / 2 + cell * selectedIndex, y)
                    val stroke = 4.dp.toPx()
                    drawLine(Colors.ChoiceTrack, first, Offset(size.width - cell / 2, y), stroke, StrokeCap.Round)
                    drawLine(primaryColor, first, selected, stroke, StrokeCap.Round)
                    for (i in choices.indices) {
                        val center = Offset(cell / 2 + cell * i, y)
                        drawCircle(if (i <= selectedIndex) primaryColor else Colors.ChoiceTrack, 5.dp.toPx(), center)
                    }
                    drawCircle(ringColor, 15.dp.toPx(), selected)
                    drawCircle(primaryColor, 11.dp.toPx(), selected)
                    drawCircle(Color.White, 4.dp.toPx(), selected)
                },
        )
        if (choices.any { it.isNotEmpty() }) Row(modifier = Modifier.fillMaxWidth()) {
            choices.forEachIndexed { i, choice ->
                val isSelected = i == selectedIndex
                Text(
                    text = choice,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else null,
                    color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
