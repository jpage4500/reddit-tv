package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jpage4500.reddittv.ui.theme.Colors
import org.jetbrains.compose.resources.DrawableResource

/**
 * rounded button with icon and text
 */
@Composable
fun AppButton(
    icon: DrawableResource? = null,
    text: String? = null,
    backgroundColor: Color = Colors.PrimaryBlue,
    color: Color = Color.White,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    shape: Shape = RoundedCornerShape(12.dp),
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        shape = shape,
        contentPadding = contentPadding
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                AppImage(
                    res = icon,
                    color = color,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (text != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = color
                )
            }
        }
    }
}
