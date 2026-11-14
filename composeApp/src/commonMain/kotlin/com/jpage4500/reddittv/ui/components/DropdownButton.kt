package com.jpage4500.reddittv.ui.components

import com.jpage4500.reddittv.ui.theme.DialogTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.ui.theme.Colors

@Composable
fun <T> DropdownButton(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    buttonText: (T) -> String,
    itemText: (T) -> String,
    buttonColor: Color = Colors.PrimaryBlue,
    textColor: Color = Color.White,
    itemLeadingIcon: @Composable ((T) -> Unit)? = null
) {
    // Manage the dropdown's expanded state internally
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = buttonColor,
                contentColor = textColor
            ),
            onClick = { expanded = !expanded },
        ) {
            Text(
                text = buttonText(selectedItem),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }

        // the popup menu looks like a dialog
        DialogTheme {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemText(item)) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    },
                    leadingIcon = itemLeadingIcon?.let { icon ->
                        { icon(item) }
                    }
                )
            }
        }
        }
    }
}