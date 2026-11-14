package com.jpage4500.reddittv.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jpage4500.reddittv.util.LoggerConfig
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.*
import reddittv.composeapp.generated.resources.*

private val log = LoggerConfig.createLogger("AppImage")

/**
 * generic icon component
 */
@Composable
fun AppIcon(
    res: DrawableResource,
    modifier: Modifier = Modifier,
    color: Color? = MaterialTheme.colorScheme.primary,
) {
    val iconColor = color ?: Color.Unspecified
    Icon(
        painter = painterResource(res),
        contentDescription = stringResource(Res.string.cd_icon),
        modifier = modifier,
        tint = iconColor
    )
}