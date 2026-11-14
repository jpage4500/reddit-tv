package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import reddittv.composeapp.generated.resources.Res
import reddittv.composeapp.generated.resources.icon_close
import reddittv.composeapp.generated.resources.search
import reddittv.composeapp.generated.resources.search_devices
import org.jetbrains.compose.resources.stringResource

/**
 * pill-shaped translucent search field with a search/clear icon on the left and placeholder text
 *
 * @param query current search text
 * @param onQueryChange called when the text changes
 * @param modifier optional modifier applied to the BasicTextField
 * @param placeholder hint text shown when query is empty
 * @param textColor color for typed text, cursor, and placeholder; defaults to white
 * @param iconColor color for the search/clear icon; defaults to white
 * @param focusRequester optional requester — pass the parent's instance when programmatic focus
 *   (e.g. CMD+F) is needed; a default is created internally when not provided
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(Res.string.search_devices),
    textColor: Color = Color.White,
    iconColor: Color = Color.White,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val focusManager = LocalFocusManager.current

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(
            color = textColor,
            fontSize = 13.sp,
            lineHeight = 16.sp
        ),
        cursorBrush = SolidColor(textColor),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = modifier
            .height(32.dp)
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20))
                    .background(textColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    res = if (query.isNotEmpty()) Res.drawable.icon_close else Res.drawable.search,
                    color = iconColor,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(enabled = query.isNotEmpty()) {
                            onQueryChange("")
                            focusManager.clearFocus()
                        }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(
                                color = textColor.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}

