package com.jpage4500.reddittv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * single control button with icon, text, and background color
 */
@Composable
fun LargeButton(
    icon: DrawableResource,
    iconColor: Color? = Color.White,
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier.Companion,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // icon - takes most of the space
            val colorFilter = if (iconColor != null) ColorFilter.tint(iconColor) else null
            Image(
                painter = painterResource(icon),
                contentDescription = text,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colorFilter = colorFilter
            )

            // text
            AppText(
                text = text,
                maxLines = 2,
                fontSize = 18.sp,
            )
        }
    }
}