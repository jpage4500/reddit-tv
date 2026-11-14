package com.jpage4500.reddittv.ui.dialog.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.ui.components.rememberTvTextInputState
import com.jpage4500.reddittv.ui.dialog.BaseDialog
import com.jpage4500.reddittv.ui.theme.Dimens
import reddittv.composeapp.generated.resources.Res
import reddittv.composeapp.generated.resources.cancel
import reddittv.composeapp.generated.resources.icon_edit
import reddittv.composeapp.generated.resources.ok
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class InputType {
    TEXT,
    NUMBER,
    DECIMAL
}

/**
 * reusable text input dialog for getting user input. [selectInitial] opens it with the field
 * focused and [initialValue] selected, so typing replaces it
 */
@Composable
fun TextInputDialog(
    title: String? = null,
    titleRes: StringResource? = null,
    desc: String? = null,
    descRes: StringResource? = null,
    initialValue: String? = null,
    selectInitial: Boolean = false,
    placeholder: String = "",
    inputType: InputType = InputType.TEXT,
    validator: ((String) -> Boolean)? = null,
    okText: String = stringResource(Res.string.ok),
    cancelText: String? = stringResource(Res.string.cancel),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember {
        val initial = initialValue ?: ""
        mutableStateOf(TextFieldValue(initial, if (selectInitial) TextRange(0, initial.length) else TextRange.Zero))
    }
    val text = value.text
    val focusRequester = remember { FocusRequester() }
    val tvInput = rememberTvTextInputState()
    val focusManager = LocalFocusManager.current

    // if input type is number or decimal - use a validator
    val validator = if (validator == null) {
        { input ->
            when (inputType) {
                InputType.NUMBER -> input.toIntOrNull() != null
                InputType.DECIMAL -> input.toDoubleOrNull() != null
                InputType.TEXT -> input.isNotEmpty()
            }
        }
    } else validator

    // Determine if the OK button should be enabled based on the validator
    val isOkEnabled = validator.invoke(text)

    // not on a TV, where focusing the field is the remote's to do
    LaunchedEffect(Unit) {
        if (selectInitial && !tvInput.readOnly) focusRequester.requestFocus()
    }

    BaseDialog(
        title = title,
        titleRes = titleRes,
        icon = Res.drawable.icon_edit,
        okText = okText,
        cancelText = cancelText,
        okEnabled = isOkEnabled,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(text) }
    ) {
        // optional description text
        val desc = desc ?: descRes?.let { stringResource(it) }
        if (desc != null) {
            Spacer(modifier = Modifier.height(8.dp))
            FormattedText(
                text = desc,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SETTING_HORIZONTAL_PADDING)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // text input field
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).then(tvInput.modifier),
            placeholder = { Text(placeholder) },
            singleLine = true,
            readOnly = tvInput.readOnly,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (inputType) {
                    InputType.TEXT -> KeyboardType.Text
                    InputType.NUMBER -> KeyboardType.Number
                    InputType.DECIMAL -> KeyboardType.Decimal
                },
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                // on a TV, Done moves on to the buttons below
                if (tvInput.onDone()) focusManager.moveFocus(FocusDirection.Down)
                else defaultKeyboardAction(ImeAction.Done)
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}