package com.jpage4500.reddittv.ui.setting

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.ui.components.AppButton
import com.jpage4500.reddittv.ui.components.AppIcon
import com.jpage4500.reddittv.ui.components.focusBorder
import com.jpage4500.reddittv.ui.theme.Dimens
import com.jpage4500.reddittv.util.ToastManager
import reddittv.composeapp.generated.resources.Res
import reddittv.composeapp.generated.resources.minus
import reddittv.composeapp.generated.resources.plus
import org.jetbrains.compose.resources.DrawableResource

/**
 * setting item with stepper controls (+/- buttons) for numeric values
 */
@Composable
fun SettingStepperItem(
    icon: DrawableResource,
    iconColor: Color? = MaterialTheme.colorScheme.primary,
    text: String,
    value: Int,
    valueText: String = value.toString(),   // displayed value (defaults to the raw int)
    valueColor: Color = Color.Red,
    minValue: Int = 1,
    maxValue: Int = 10,
    defaultValue: Int? = null,   // when [value] equals this, the value is bolded to mark it as the default
    onValueChange: (Int) -> Unit
) {
    val isDefault = defaultValue != null && value == defaultValue
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SETTING_HORIZONTAL_PADDING)
            .focusBorder()
            .combinedClickable(
                onClick = { },
                onLongClick = { ToastManager.showToast("$text - $valueText") }
            ),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // icon
        AppIcon(
            res = icon,
            modifier = Modifier.size(Dimens.SETTING_ICON_SIZE),
            color = iconColor
        )

        Spacer(modifier = Modifier.width(Dimens.SETTING_ITEM_PADDING))

        // main text
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = Dimens.SETTING_LABEL_SIZE,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            softWrap = false
        )

        Spacer(modifier = Modifier.width(Dimens.SETTING_ITEM_PADDING))

        // stepper controls
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // minus button
            AppButton(
                icon = Res.drawable.minus,
                backgroundColor = if (value > minValue) MaterialTheme.colorScheme.primary else Color.Gray,
                onClick = {
                    if (value > minValue) {
                        onValueChange(value - 1)
                    }
                },
                modifier = Modifier.size(38.dp),
                contentPadding = PaddingValues(10.dp)
            )

            // value display - centered between buttons with fixed width
            Box(
                modifier = Modifier.width(42.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = Dimens.SETTING_VALUE_SIZE,
                    color = valueColor,
                    fontWeight = if (isDefault) FontWeight.Bold else null,
                )
            }

            // plus button
            AppButton(
                icon = Res.drawable.plus,
                backgroundColor = if (value < maxValue) MaterialTheme.colorScheme.primary else Color.Gray,
                onClick = {
                    if (value < maxValue) {
                        onValueChange(value + 1)
                    }
                },
                modifier = Modifier.size(38.dp),
                contentPadding = PaddingValues(10.dp)
            )

        }
    }
}

