package com.jpage4500.reddittv.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp

/**
 * the menu's line icons, drawn after reddit's own (a house for Home, an arrow in a circle for
 * Popular, …). vectors in code rather than drawables: compose resources can't decode SVG on Android
 */
object MenuIcons {
    /** a house: Home */
    val Home: ImageVector by lazy { outline("home", "M4,10.5L12,4l8,6.5V20h-5v-5.5h-6V20H4z") }

    /** an arrow heading up and right in a circle: Popular */
    val Popular: ImageVector by lazy {
        outline("popular", "M21,12A9,9 0 1,1 3,12A9,9 0 1,1 21,12zM8.5,15.5L15.5,8.5M10.5,8.5h5v5")
    }

    /** rising bars: All */
    val All: ImageVector by lazy { outline("all", "M5,20V14M10,20V9M15,20V12M20,20V4") }

    /** a magnifying glass: Search */
    val Search: ImageVector by lazy { outline("search", "M17,10.5A6.5,6.5 0 1,1 4,10.5A6.5,6.5 0 1,1 17,10.5zM15.5,15.5L20,20") }

    /** a cog: Settings */
    val Settings: ImageVector by lazy {
        outline(
            "settings",
            "M10.3,3L13.7,3L14.2,5.5L15.9,6.5L18.3,5.65L20,8.6L18.1,10.25L18.1,13.75L20,15.4L18.3,18.35" +
                "L15.9,17.5L14.2,18.5L13.7,21L10.3,21L9.8,18.5L8.1,17.5L5.7,18.35L4,15.4L5.9,13.75L5.9,10.25" +
                "L4,8.6L5.7,5.65L8.1,6.5L9.8,5.5z" +
                "M15,12A3,3 0 1,1 9,12A3,3 0 1,1 15,12z",
        )
    }

    /** a head and shoulders: Sign in */
    val Account: ImageVector by lazy {
        outline("account", "M15.5,8A3.5,3.5 0 1,1 8.5,8A3.5,3.5 0 1,1 15.5,8zM5,20c0,-3.9 3.1,-6 7,-6s7,2.1 7,6")
    }

    // a 24x24 icon drawn as a line; Icon's tint sets its color
    private fun outline(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = addPathNodes(pathData),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}
