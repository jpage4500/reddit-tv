package com.jpage4500.reddittv.ui.dialog.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpage4500.reddittv.ui.components.SearchField
import com.jpage4500.reddittv.ui.dialog.BaseDialog
import com.jpage4500.reddittv.ui.theme.Dimens
import com.jpage4500.reddittv.util.ToastManager
import reddittv.composeapp.generated.resources.Res
import reddittv.composeapp.generated.resources.cancel
import reddittv.composeapp.generated.resources.ok
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * represents a single option in the options dialog
 */
data class DialogOption<T>(
    val icon: DrawableResource? = null,
    val label: String? = null,
    val textRes: StringResource? = null,
    val data: T,
    val value: String? = null,
    val valueRes: StringResource? = null,
    val valueColor: Color = Color.Red,
)

/**
 * generic options dialog that displays a list of options with radio buttons
 * each option can have an optional icon and text
 */
@Composable
fun <T> OptionsDialog(
    title: String,
    message: String? = null,
    options: List<DialogOption<T>>,
    selectedValue: T? = null,
    // label for the confirm button, given whatever is selected right now. a choice that carries on
    // into another dialog — picking a source that needs an API key — says NEXT rather than OK
    okTextFor: ((T?) -> String)? = null,
    onDismiss: () -> Unit,
    onConfirm: (T) -> Unit,
    // long-pressing an option: called with whatever was pressed. when unset, the option's label and
    // value are shown as a toast — the same fallback SettingItem uses, so a value the row ellipsized
    // is always readable
    onLongClick: ((T) -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    var currentSelection by remember { mutableStateOf(selectedValue) }
    var searchQuery by remember { mutableStateOf("") }

    // Resolve display strings to allow for easy filtering
    val optionsWithText = options.map { option ->
        val displayText = option.textRes?.let { stringResource(it) } ?: option.label.orEmpty()
        option to displayText
    }

    // Filter based on search query
    val filteredOptions = if (searchQuery.isEmpty()) {
        optionsWithText
    } else {
        optionsWithText.filter { it.second.contains(searchQuery, ignoreCase = true) }
    }

    BaseDialog(
        title = title,
        okText = okTextFor?.invoke(currentSelection) ?: stringResource(Res.string.ok),
        cancelText = stringResource(Res.string.cancel),
        onConfirm = {
            currentSelection?.let {
                onConfirm(it)
            }
        },
        onDismiss = onDismiss,
    ) {
        message?.let {
            // message
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

        // Show SearchField if there are 10 or more total options
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

        filteredOptions.forEach { (option, displayText) ->
            OptionItem(
                option = option,
                displayText = displayText,
                isSelected = currentSelection == option.data,
                onClick = { currentSelection = option.data },
                onLongClick = onLongClick,
            )
        }

        content?.invoke()
    }
}

/**
 * single option item with radio button, optional icon, and text
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> OptionItem(
    option: DialogOption<T>,
    displayText: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: ((T) -> Unit)? = null,
) {
    // resolved here as well as below so the fallback toast can name the value
    val valueText = option.valueRes?.let { stringResource(it) } ?: option.value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { selected = isSelected }
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (onLongClick != null) {
                        onLongClick(option.data)
                    } else {
                        val toast = listOfNotNull(displayText, valueText).joinToString(" - ")
                        ToastManager.showToast(toast)
                    }
                },
                role = Role.RadioButton
            )
            .padding(horizontal = Dimens.SETTING_HORIZONTAL_PADDING, vertical = 10.dp),
        //.padding(horizontal = Dimens.SETTING_HORIZONTAL_PADDING, vertical = Dimens.SETTING_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // radio button
        RadioButton(
            selected = isSelected,
            onClick = null // handled by row's selectable
        )

        //Spacer(modifier = Modifier.width(Dimens.SETTING_ITEM_PADDING))
        Spacer(modifier = Modifier.width(16.dp))

        // optional icon
        option.icon?.let { icon ->
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        // label - natural width, takes priority so it always fits
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = Dimens.SETTING_LABEL_SIZE,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
        )

        // optional value: smaller, colored, single-line ellipsized, takes remaining space
        if (valueText != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = Dimens.SETTING_VALUE_SIZE,
                color = option.valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}