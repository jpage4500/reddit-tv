package com.jpage4500.reddittv.ui.setting

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.ui.components.AppIcon
import com.jpage4500.reddittv.ui.components.AppImage
import com.jpage4500.reddittv.ui.components.focusBorder
import com.jpage4500.reddittv.ui.components.rememberFocus
import com.jpage4500.reddittv.ui.theme.Dimens
import com.jpage4500.reddittv.util.LoggerConfig
import com.jpage4500.reddittv.util.ToastManager
import reddittv.composeapp.generated.resources.Res
import reddittv.composeapp.generated.resources.help
import reddittv.composeapp.generated.resources.icon_help
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val log = LoggerConfig.createLogger("SettingItem")

/**
 * reusable setting item with icon, text, and optional value
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingItem(
    icon: DrawableResource,                 // icon (left-aligned) — fallback when iconUrl is null
    iconColor: Color? = MaterialTheme.colorScheme.primary,
    iconUrl: String? = null,                // optional image url shown (circular) in place of icon
    labelText: String? = null,              // label (string version)
    labelRes: StringResource? = null,       // label (resource version)
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    valueText: String? = null,              // value (string version)
    valueRes: StringResource? = null,       // value (resource version)
    valueColor: Color = MaterialTheme.colorScheme.primary,
    valueIcon: DrawableResource? = null,    // value icon (right-aligned - only displayed if no value)
    valueIconColor: Color? = MaterialTheme.colorScheme.primary,
    valueContent: (@Composable () -> Unit)? = null, // custom trailing content (right-aligned; used when no value text, takes priority over valueIcon)
    prioritizeValue: Boolean = false,       // when too narrow to fit both: false = keep label (truncate value), true = keep value (truncate label)
    showDivider: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    // label can come from multiple inputs, first of: textRes or text
    val label: String = if (labelRes != null) stringResource(labelRes) else labelText.orEmpty()
    // value can come from multiple inputs, first of: textRes or text
    val value = if (valueRes != null) stringResource(valueRes) else valueText

    // column to add divider line below setting
    Column(modifier = Modifier.fillMaxWidth()) {
        // row: "[icon] [label]   [value]"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .rememberFocus(label)
                .padding(vertical = Dimens.SETTING_FOCUS_GAP)
                .focusBorder()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (onLongClick != null) {
                            onLongClick()
                        } else {
                            val toast = listOfNotNull(label, value).joinToString(" - ")
                            ToastManager.showToast(toast)
                        }
                    }
                )
                .padding(horizontal = Dimens.SETTING_HORIZONTAL_PADDING, vertical = Dimens.SETTING_VERTICAL_PADDING),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // whichever element isn't prioritized takes the remaining space and truncates;
            // the prioritized one keeps its full size
            val labelModifier = if (prioritizeValue) Modifier.weight(1f) else Modifier
            val valueModifier = if (prioritizeValue) Modifier else Modifier.weight(1f)

            // icon — show the image url (circular) when provided, else the drawable icon
            if (iconUrl != null) {
                AppImage(
                    url = iconUrl,
                    modifier = Modifier.size(Dimens.SETTING_ICON_SIZE).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                AppIcon(
                    res = icon,
                    modifier = Modifier.size(Dimens.SETTING_ICON_SIZE),
                    color = iconColor
                )
            }

            // small space to separate setting items
            Spacer(modifier = Modifier.width(Dimens.SETTING_ITEM_PADDING))

            // label
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = Dimens.SETTING_LABEL_SIZE,
                maxLines = 1,
                color = labelColor,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = labelModifier,
            )

            // small space to separate setting items
            Spacer(modifier = Modifier.width(Dimens.SETTING_ITEM_PADDING))

            // optional value (right-aligned)
            if (value != null) {
                // value display
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = Dimens.SETTING_VALUE_SIZE,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    color = valueColor,
                    modifier = valueModifier,
                )
            } else if (valueContent != null) {
                // optional custom trailing content
                if (!prioritizeValue) {
                    // label isn't weighted here, so push the content to the right
                    Spacer(modifier = Modifier.weight(1f))
                }
                valueContent()
            } else if (valueIcon != null) {
                // optional value icon
                if (!prioritizeValue) {
                    // label isn't weighted here, so push the icon to the right
                    Spacer(modifier = Modifier.weight(1f))
                }
                AppIcon(
                    res = valueIcon,
                    modifier = Modifier.size(28.dp),
                    color = valueIconColor
                )
            }
        }

        // optional divider below the setting item
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }

}

@Composable
fun SettingHelpItem(
    res: StringResource? = null,
    text: String? = null,
) {
    val helpText = if (res != null) stringResource(res) else text.orEmpty()
    SettingItem(
        icon = Res.drawable.icon_help,
        labelRes = Res.string.help,
        showDivider = false,
        onClick = { ToastManager.showToast(helpText) },
    )
}