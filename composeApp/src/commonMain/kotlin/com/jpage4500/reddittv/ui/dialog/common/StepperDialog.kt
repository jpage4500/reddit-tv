package com.jpage4500.reddittv.ui.dialog.common

import androidx.compose.runtime.*
import com.jpage4500.reddittv.ui.dialog.BaseDialog
import com.jpage4500.reddittv.ui.setting.SettingStepperItem
import org.jetbrains.compose.resources.DrawableResource

/**
 * dialog for adjusting a numeric value with stepper controls (+/- buttons)
 */
@Composable
fun StepperDialog(
    title: String,
    icon: DrawableResource,
    initialValue: Int,
    minValue: Int = 1,
    maxValue: Int = 10,
    onDismiss: () -> Unit,
    onValueChange: (Int) -> Unit
) {
    var currentValue by remember { mutableStateOf(initialValue) }

    BaseDialog(onDismiss = onDismiss, title = title) {

        SettingStepperItem(
            icon = icon,
            text = title,
            value = currentValue,
            minValue = minValue,
            maxValue = maxValue,
            onValueChange = { value ->
                currentValue = value
                onValueChange(value)
            }
        )
    }
}

