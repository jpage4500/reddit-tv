package com.jpage4500.reddittv.ui.setting

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.jpage4500.reddittv.ui.components.AppIcon
import com.jpage4500.reddittv.ui.components.AppImage
import com.jpage4500.reddittv.ui.components.focusBorder
import com.jpage4500.reddittv.ui.components.rememberFocus
import com.jpage4500.reddittv.ui.theme.Dimens
import com.jpage4500.reddittv.util.ToastManager
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * setting item with a switch control. uncontrolled by default; pass `checked` for
 * controlled mode (parent owns state — useful for mirroring OS permissions that
 * can change outside the app).
 */
@Composable
fun SettingSwitchItem(
    icon: DrawableResource,                 // left-side icon (DrawableResource)
    iconColor: Color? = MaterialTheme.colorScheme.primary,
    iconUrl: String? = null,                // left-side image
    labelText: String? = null,              // label (string version)
    labelRes: StringResource? = null,       // label (resource version)
    descText: String? = null,               // optional second line below the label (string version)
    descRes: StringResource? = null,        // optional second line below the label (resource version)
    initialChecked: Boolean = false,
    checked: Boolean? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    // label can come from multiple inputs, first of: textRes or text
    val label = labelRes?.let { stringResource(it) } ?: labelText.orEmpty()
    // optional description — for a setting whose label can't say what it actually does
    val desc = descRes?.let { stringResource(it) } ?: descText

    // controlled when `checked` is non-null; otherwise fall back to internal state
    var internalChecked by remember { mutableStateOf(initialChecked) }
    val isChecked = checked ?: internalChecked

    val contentAlpha = if (enabled) 1f else 0.38f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .rememberFocus(label)
                .padding(vertical = Dimens.SETTING_FOCUS_GAP)
                .focusBorder()
                .combinedClickable(
                    enabled = enabled,
                    onClick = {
                        val newValue = !isChecked
                        if (checked == null) internalChecked = newValue
                        onCheckedChange?.invoke(newValue)
                    },
                    onLongClick = {
                        if (onLongClick != null) {
                            onLongClick()
                        } else {
                            ToastManager.showToast(label)
                        }
                    }
                )
                .padding(horizontal = Dimens.SETTING_HORIZONTAL_PADDING, vertical = Dimens.SETTING_VERTICAL_PADDING),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // icon — show the image url (circular) when provided, else the drawable icon
            if (iconUrl != null) {
                AppImage(
                    url = iconUrl,
                    modifier = Modifier.size(Dimens.SETTING_ICON_SIZE).clip(CircleShape).alpha(contentAlpha),
                    contentScale = ContentScale.Crop,
                )
            } else {
                AppIcon(
                    res = icon,
                    modifier = Modifier.size(Dimens.SETTING_ICON_SIZE).alpha(contentAlpha),
                    color = iconColor
                )
            }

            // small space to separate setting items
            Spacer(modifier = Modifier.width(Dimens.SETTING_ITEM_PADDING))

            // main text, with the description below it when there is one
            Column(modifier = Modifier.weight(1f).alpha(contentAlpha)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = Dimens.SETTING_LABEL_SIZE,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )

                if (desc != null) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // small space to separate setting items
            Spacer(modifier = Modifier.width(Dimens.SETTING_ITEM_PADDING))

            // switch control - constrain height to match icon size
            Switch(
                checked = isChecked,
                enabled = enabled,
                // handled by the row — a live switch would be a second focus stop for a remote
                onCheckedChange = null,
                modifier = Modifier.height(Dimens.SETTING_ICON_SIZE)
            )
        }

        // optional divider below the setting item
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}





