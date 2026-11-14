package com.jpage4500.reddittv.ui.dialog.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpage4500.reddittv.data.Branding
import com.jpage4500.reddittv.ui.components.SearchField
import com.jpage4500.reddittv.ui.dialog.BaseDialog
import com.jpage4500.reddittv.ui.theme.Dimens.Companion.SETTING_HORIZONTAL_PADDING
import com.jpage4500.reddittv.ui.theme.Dimens.Companion.SETTING_ITEM_PADDING
import com.jpage4500.reddittv.ui.theme.Dimens.Companion.SETTING_VERTICAL_PADDING
import com.jpage4500.reddittv.util.ToastManager
import com.jpage4500.reddittv.util.Utils
import reddittv.composeapp.generated.resources.Res
import reddittv.composeapp.generated.resources.cancel
import reddittv.composeapp.generated.resources.ok
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * represents a single option in the multi-select options dialog
 */
data class MultiSelectDialogOption<T>(
    val data: T, // returned in a list when item is checked
    val icon: DrawableResource? = null,
    val textRes: StringResource? = null,
    val text: String? = null,
    val value: String? = null,
    val valueRes: StringResource? = null,
    val valueColor: Color = Color.Red,
)

/**
 * generic multi-select options dialog that displays a list of options with checkboxes
 * each option can have an optional icon and text
 */
@Composable
fun <T> MultiSelectOptionsDialog(
    title: String? = null,
    titleRes: StringResource? = null,
    message: String? = null,
    options: List<MultiSelectDialogOption<T>>,
    selectedValues: Set<T>,
    // true = allow long option labels to wrap onto multiple lines (default truncates to one line)
    wrapLabels: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Set<T>) -> Unit
) {
    val title = title ?: titleRes?.let { stringResource(it) } ?: Branding.appName

    var currentSelection by remember { mutableStateOf(selectedValues.toMutableSet()) }
    var searchQuery by remember { mutableStateOf("") }

    // resolve display strings to allow for easy filtering
    val optionsWithText = options.map { option ->
        val label = option.textRes?.let { stringResource(it) } ?: option.text.orEmpty()
        val value = option.valueRes?.let { stringResource(it) } ?: option.value
        Triple(option, label, value)
    }

    // filter based on search query
    val filteredOptions = if (searchQuery.isEmpty()) {
        optionsWithText
    } else {
        optionsWithText.filter { it.second.contains(searchQuery, ignoreCase = true) }
    }

    BaseDialog(
        title = title,
        okText = stringResource(Res.string.ok),
        cancelText = stringResource(Res.string.cancel),
        // nothing to apply when no options are checked
        okEnabled = currentSelection.isNotEmpty(),
        onConfirm = { onConfirm(currentSelection) },
        onDismiss = onDismiss,
    ) {
        message?.let {
            Text(
                text = message,
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // show SearchField if there are 10 or more total options
        if (options.size >= 10) {
            SearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                textColor = MaterialTheme.colorScheme.onSurface,
                iconColor = MaterialTheme.colorScheme.onSurface
            )
        }

        filteredOptions.forEach { (option, label, value) ->
            MultiSelectOptionItem(
                option = option,
                label = label,
                value = value,
                isSelected = currentSelection.contains(option.data),
                wrapLabels = wrapLabels,
                onClick = {
                    currentSelection = if (currentSelection.contains(option.data)) {
                        // remove from selection
                        currentSelection.toMutableSet().apply { remove(option.data) }
                    } else {
                        // add to selection
                        currentSelection.toMutableSet().apply { add(option.data) }
                    }
                }
            )
        }
    }
}

/**
 * single option item with checkbox, optional icon, and text
 */
@Composable
private fun <T> MultiSelectOptionItem(
    option: MultiSelectDialogOption<T>,
    label: String,
    value: String?,
    isSelected: Boolean,
    wrapLabels: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Checkbox,
                onClick = { onClick() },
                onLongClick = {
                    if (value != null) ToastManager.showToast("$label: $value")
                    else ToastManager.showToast(label)
                }
            )
            .padding(horizontal = SETTING_HORIZONTAL_PADDING, vertical = SETTING_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // checkbox
        Checkbox(
            checked = isSelected,
            onCheckedChange = null // handled by row's toggleable
        )

        // small space to separate setting items
        Spacer(modifier = Modifier.width(SETTING_ITEM_PADDING))

        // optional icon
        option.icon?.let { icon ->
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(28.dp)
            )
            // small space to separate setting items
            Spacer(modifier = Modifier.width(SETTING_ITEM_PADDING))
        }

        // label - natural width so it always fits; when wrapLabels is set the label instead
        // takes the remaining width and wraps onto multiple lines
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 18.sp,
            maxLines = if (wrapLabels) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = wrapLabels,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            modifier = if (wrapLabels) Modifier.weight(1f) else Modifier
        )

        // small space to separate setting items
        Spacer(modifier = Modifier.width(SETTING_ITEM_PADDING))

        // optional value (takes remaining space, right-aligned, truncated with ellipsis if too long)
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 18.sp,
                color = option.valueColor,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

