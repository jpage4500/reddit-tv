package com.jpage4500.reddittv.ui.dialog.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpage4500.reddittv.ui.dialog.BaseDialog
import com.jpage4500.reddittv.util.DateUtils
import reddittv.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * pick a start and an end time-of-day, both as minutes past midnight.
 *
 * one clock, not two: a [TimePicker] each would be twice the height a dialog has, so the two times
 * are tabs above a single dial and the selected one is what the dial edits. an end before the start
 * is a valid answer — it's a window that runs overnight — so nothing here rejects it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangeDialog(
    title: String,
    startMins: Int,
    endMins: Int,
    message: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (startMins: Int, endMins: Int) -> Unit,
) {
    val startState = rememberTimePickerState(
        initialHour = startMins / 60,
        initialMinute = startMins % 60,
        is24Hour = false,
    )
    val endState = rememberTimePickerState(
        initialHour = endMins / 60,
        initialMinute = endMins % 60,
        is24Hour = false,
    )
    var editingStart by remember { mutableStateOf(true) }

    BaseDialog(
        title = title,
        icon = Res.drawable.icon_clock,
        okText = stringResource(Res.string.ok),
        cancelText = stringResource(Res.string.cancel),
        onConfirm = {
            onConfirm(
                startState.hour * 60 + startState.minute,
                endState.hour * 60 + endState.minute,
            )
        },
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 15.sp,
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TimeTab(
                labelRes = Res.string.start_time,
                mins = startState.hour * 60 + startState.minute,
                isSelected = editingStart,
                onClick = { editingStart = true },
                modifier = Modifier.weight(1f),
            )
            TimeTab(
                labelRes = Res.string.end_time,
                mins = endState.hour * 60 + endState.minute,
                isSelected = !editingStart,
                onClick = { editingStart = false },
                modifier = Modifier.weight(1f),
            )
        }

        // both states are held above; keyed so the dial rebuilds against the one it's now editing
        key(editingStart) {
            TimePicker(
                state = if (editingStart) startState else endState,
                layoutType = TimePickerLayoutType.Vertical,
            )
        }
    }
}

/**
 * one of the two times, and the tab that points the dial at it
 */
@Composable
private fun TimeTab(
    labelRes: StringResource,
    mins: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = DateUtils.formatMinsOfDay(mins),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
