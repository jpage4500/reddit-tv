package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * wraps any composable content with an OutlinedTextField-style border and floating label
 * @param label text displayed as a floating label on the top border
 * @param modifier optional modifier applied to the outer container
 * @param content the composable content to display inside the border
 */
@Composable
fun OutlinedLabel(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outline
    val bgColor = MaterialTheme.colorScheme.surface
    Box(modifier = modifier.fillMaxWidth()) {
        // bordered container — top padding leaves room for the floating label
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp)
        ) {
            content()
        }
        // floating label sits on top of the border line, matching OutlinedTextField style
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 12.dp)
                .background(bgColor)
                .padding(horizontal = 4.dp)
        )
    }
}

