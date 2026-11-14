package com.jpage4500.reddittv.ui.dialog.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.jpage4500.reddittv.ui.dialog.BaseDialog
import com.jpage4500.reddittv.ui.setting.SettingSliderItem
import com.jpage4500.reddittv.ui.theme.Dimens
import org.jetbrains.compose.resources.DrawableResource

/**
 * dialog for adjusting a numeric value with a slider
 */
@Composable
fun SliderDialog(
    title: String,
    icon: DrawableResource,
    initialValue: Int,
    valueDesc: String? = null,
    valueRange: IntRange,
    steps: Int = valueRange.last - valueRange.first - 1, // pass 0 for a smooth slider over a wide range
    defaultValue: Int? = null,
    desc: String? = null,
    // when non-null, enables tap-to-type manual entry with this label (e.g. "Enter value in seconds");
    // when null, manual entry is disabled. only set it where the value is the real quantity typed,
    // not an index into a preset list/enum (see SettingSliderItem)
    manualEntryLabel: String? = null,
    onDismiss: () -> Unit,
    onValueChange: ((Int) -> Pair<String?, String?>?)? = null,
    onValueChangeFinished: ((Int) -> Unit)? = null, // notify when user stopped changing value
    okText: String? = null,         // optional OK button
    cancelText: String? = null,     // optional CANCEL button
    onConfirm: ((Int) -> Unit)? = null,
) {
    var currentValue by remember { mutableStateOf(initialValue) }
    var currentDesc by remember { mutableStateOf(desc) }

    // NOTE: hide DeviceTitle
    BaseDialog(
        title = title,
        icon = icon,
        onDismiss = onDismiss,
        okText = okText,
        cancelText = cancelText,
        onConfirm = {
            onConfirm?.invoke(currentValue)
        }
    ) {
        // slider content
        SettingSliderItem(
            icon = icon,
            text = title,
            value = currentValue,
            valueDesc = valueDesc,
            valueRange = valueRange,
            steps = steps,
            defaultValue = defaultValue,
            manualEntryLabel = manualEntryLabel,
            showDivider = false,
            onValueChange = { newValue ->
                currentValue = newValue
                val resultPair = onValueChange?.invoke(newValue)
                currentDesc = resultPair?.second
                resultPair?.first
            },
            onValueChangeFinished = onValueChangeFinished,
        )

        val desc = currentDesc;
        if (desc != null) {
            Text(
                text = desc,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = Dimens.SETTING_HORIZONTAL_PADDING)
                ,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

