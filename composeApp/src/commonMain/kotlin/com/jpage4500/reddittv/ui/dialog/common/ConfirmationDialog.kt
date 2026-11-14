package com.jpage4500.reddittv.ui.dialog.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.ui.dialog.BaseDialog
import reddittv.composeapp.generated.resources.Res
import reddittv.composeapp.generated.resources.cancel
import reddittv.composeapp.generated.resources.icon_info
import reddittv.composeapp.generated.resources.ok
import org.jetbrains.compose.resources.stringResource

/**
 * reusable confirmation dialog
 * @param okText - OK button text (default: "OK")
 * @param cancelText - cancel button text (default: "CANCEL")
 * @param onConfirm - optional listener called when user hits OK
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    okText: String? = stringResource(Res.string.ok),
    cancelText: String? = stringResource(Res.string.cancel),
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)? = null,
) {
    BaseDialog(
        title = title,
        icon = Res.drawable.icon_info,
        okText = okText,
        cancelText = cancelText,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    ) {
        FormattedText(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
    }
}