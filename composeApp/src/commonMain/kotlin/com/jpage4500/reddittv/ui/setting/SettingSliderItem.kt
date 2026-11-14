package com.jpage4500.reddittv.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jpage4500.reddittv.ui.dialog.common.CustomSlider
import com.jpage4500.reddittv.ui.dialog.common.InputType
import com.jpage4500.reddittv.ui.dialog.common.TextInputDialog
import com.jpage4500.reddittv.ui.theme.Dimens
import com.jpage4500.reddittv.util.LoggerConfig
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val log = LoggerConfig.createLogger("SettingSliderItem")

/**
 * setting item with a slider control
 */
@Composable
fun SettingSliderItem(
    icon: DrawableResource,
    text: String? = null,
    textRes: StringResource? = null,
    value: Int,
    valueRange: IntRange,
    valueDesc: String? = null,
    defaultValue: Int? = null, // Changed to Int
    steps: Int = valueRange.last - valueRange.first - 1,
    primaryColor: Color = Color(0xFF0D47A1),
    innerCircleColor: Color = Color.White,
    showDivider: Boolean = true,
    // when non-null, tapping the row opens a type-a-value dialog using this as its title/description
    // (e.g. "Enter value in seconds"); when null, manual entry is disabled entirely. only enable it
    // where the slider value is the real quantity being typed — NOT where it's an index into a
    // preset list/enum, since manual entry is intentionally NOT clamped to valueRange
    manualEntryLabel: String? = null,
    onValueChange: ((Int) -> String?)? = null,
    onValueChangeFinished: ((Int) -> Unit)? = null,
) {
    // Changed to mutableIntStateOf for better performance with Ints
    var currentValue by remember { mutableIntStateOf(value) }
    var valueText by remember { mutableStateOf(valueDesc ?: value.toString()) }

    var showInputDialog by remember { mutableStateOf(false) }

    // sync external updates
    LaunchedEffect(value) {
        currentValue = value
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // header row with icon, text, and value
        // NOTE: padding built-in
        SettingItem(
            icon = icon,
            labelText = text,
            labelRes = textRes,
            valueText = valueText,
            showDivider = false, // add divider below
            // only open the type-a-value dialog when manual entry is enabled for this slider
            onClick = { if (manualEntryLabel != null) showInputDialog = true },
        )

        // column to match padding from SettingItem
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = Dimens.SETTING_HORIZONTAL_PADDING)
        ) {

            // custom slider control
            CustomSlider(
                steps = steps,
                startValue = valueRange.first,
                endValue = valueRange.last,
                value = currentValue,
                defaultValue = defaultValue,
                primaryColor = primaryColor,
                innerCircleColor = innerCircleColor,
                onValueChange = { sliderValue ->
                    if (currentValue != sliderValue) {
                        currentValue = sliderValue
                        // Removed .toInt() since sliderValue is already an Int
                        valueText = onValueChange?.invoke(sliderValue) ?: sliderValue.toString()
                    }
                },
                onValueChangeFinished = { finalValue ->
                    // Removed .toInt()
                    onValueChangeFinished?.invoke(finalValue)
                },
            )

            // optional divider below the setting item
            if (showDivider) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }

    // manual numeric entry (opt-in via manualEntryLabel) — no validator so the value is NOT clamped
    // to valueRange, letting the user type an exact value (incl. beyond the slider's max)
    if (showInputDialog && manualEntryLabel != null) {
        TextInputDialog(
            title = manualEntryLabel,
            initialValue = currentValue.toString(),
            inputType = InputType.NUMBER,
            onDismiss = { showInputDialog = false },
            onConfirm = { input ->
                input.toIntOrNull()?.let { newValue ->
                    currentValue = newValue
                    valueText = onValueChange?.invoke(newValue) ?: newValue.toString()
                    onValueChangeFinished?.invoke(newValue)
                }
                showInputDialog = false
            }
        )
    }
}