package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jpage4500.reddittv.ui.dialog.BaseDialog
import com.jpage4500.reddittv.util.DateUtils
import reddittv.composeapp.generated.resources.*
import kotlinx.datetime.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initialDateTime: LocalDateTime? = null,
    showDate: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    val initialDateTime = initialDateTime ?: DateUtils.getNow()
    var selectedDate by remember { mutableStateOf(initialDateTime.date) }

    val timeState = rememberTimePickerState(
        initialHour = initialDateTime.hour,
        initialMinute = initialDateTime.minute,
        is24Hour = false,
    )

    // Convert initial date to millis for DatePicker
    val initialMillis = remember(initialDateTime) {
        initialDateTime
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
    }

    val dateState = if (showDate) {
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )
    } else null

    BaseDialog(
        title = if (showDate)
            stringResource(Res.string.select_date_time)
        else stringResource(Res.string.select_time),
        icon = Res.drawable.icon_clock,
        okText = stringResource(Res.string.ok),
        cancelText = stringResource(Res.string.cancel),
        onConfirm = {
            val hour = timeState.hour
            val minute = timeState.minute

            val finalDate = if (showDate && dateState?.selectedDateMillis != null) {
                val picked = Instant
                    .fromEpochMilliseconds(dateState.selectedDateMillis!!)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                picked.date
            } else {
                selectedDate // fallback
            }

            val result = LocalDateTime(
                year = finalDate.year,
                monthNumber = finalDate.month.number,
                dayOfMonth = finalDate.day,
                hour = hour,
                minute = minute,
                second = 0,
                nanosecond = 0
            )

            onConfirm(result)
        },
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier,
    ) {

        // Date picker
        if (showDate && dateState != null) {
            DatePicker(
                state = dateState,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Time picker
        TimePicker(
            state = timeState,
            layoutType = TimePickerLayoutType.Vertical,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}