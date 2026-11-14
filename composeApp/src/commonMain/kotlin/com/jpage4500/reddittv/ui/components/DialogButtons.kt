package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpage4500.reddittv.ui.theme.Colors
import reddittv.composeapp.generated.resources.Res
import reddittv.composeapp.generated.resources.cancel
import reddittv.composeapp.generated.resources.ok
import org.jetbrains.compose.resources.stringResource

/**
 * reusable button row with cancel and ok buttons
 */
@Composable
fun DialogButtons(
    okText: String? = stringResource(Res.string.ok),
    cancelText: String? = stringResource(Res.string.cancel),
    onOk: () -> Unit,
    onCancel: () -> Unit,
    okEnabled: Boolean = true,          // if OK button is enabled (only applies if okText is not null)
    okFocusRequester: FocusRequester? = null,   // lets the caller focus the OK button
    modifier: Modifier = Modifier
) {
    // Button always draws its ripple, whose focus tint a remote can't see — so the fill shows focus
    val cancelSource = remember { MutableInteractionSource() }
    val okSource = remember { MutableInteractionSource() }
    val isCancelFocused by cancelSource.collectIsFocusedAsState()
    val isOkFocused by okSource.collectIsFocusedAsState()
    // the focused button is the highlighted one. with neither focused (touch, or focus up in the
    // content) OK keeps the highlight, as the default action
    val isOkHighlighted = isOkFocused || !isCancelFocused

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // cancel button
        if (cancelText != null) {
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                interactionSource = cancelSource,
                colors = dialogButtonColors(isCancelFocused),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    cancelText.uppercase(),
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ok button
        if (okText != null) {
            Button(
                onClick = onOk,
                enabled = okEnabled,
                modifier = Modifier
                    .weight(1f)
                    .let { if (okFocusRequester != null) it.focusRequester(okFocusRequester) else it },
                interactionSource = okSource,
                colors = dialogButtonColors(isOkHighlighted),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    okText.uppercase(),
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** the highlighted button is filled with the primary color, the other is gray */
@Composable
private fun dialogButtonColors(isHighlighted: Boolean): ButtonColors =
    if (isHighlighted) ButtonDefaults.buttonColors()
    else ButtonDefaults.buttonColors(containerColor = Colors.DialogButton, contentColor = Color.White)
