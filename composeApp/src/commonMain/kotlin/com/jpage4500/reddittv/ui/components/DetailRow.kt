package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpage4500.reddittv.ui.theme.Colors
import com.jpage4500.reddittv.util.PlatformType
import com.jpage4500.reddittv.util.PlatformUtils
import com.jpage4500.reddittv.util.ToastManager
import reddittv.composeapp.generated.resources.*
import com.jpage4500.reddittv.util.LoggerConfig
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val log = LoggerConfig.createLogger("DetailRow")

/** a label and its value, as the details dialogs list them. a click copies the value */
@Composable
fun DetailRow(labelRes: StringResource, value: String, labelWidth: Dp) {
    DetailRow(labelRes, copyText = value, labelWidth = labelWidth) {
        Text(text = value, fontSize = 14.sp)
    }
}

/** a label and any content, where [copyText] is what a click puts on the clipboard */
@Suppress("DEPRECATION")
@Composable
fun DetailRow(
    labelRes: StringResource,
    copyText: String,
    labelWidth: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val label = stringResource(labelRes)
    val copiedMessage = stringResource(Res.string.detail_copied, label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                clipboard.setText(AnnotatedString(copyText))
                log.d("Copied $label: $copyText")
                if (!systemShowsCopied()) ToastManager.showToast(copiedMessage)
            }
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = Colors.DarkGray,
            fontSize = 14.sp,
            modifier = Modifier.width(labelWidth),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

/** Android 13+ phones confirm every clipboard write themselves; a second toast just repeats it */
private fun systemShowsCopied(): Boolean =
    PlatformUtils.getPlatform() == PlatformType.ANDROID &&
        !PlatformUtils.isTv() &&
        (PlatformUtils.getPlatformVersion().toIntOrNull() ?: 0) >= 33
