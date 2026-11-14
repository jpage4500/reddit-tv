package com.jpage4500.reddittv.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp

/**
 * the outline icons in a post's vote and comment pills. vectors in code rather than drawables:
 * compose resources can't decode SVG on Android
 */
object MetaIcons {
    /** an arrow pointing up: the score */
    val Upvote: ImageVector by lazy { outline("upvote", "M12,4L4,13h5v7h6v-7h5z") }

    /** a round speech bubble: the comment count */
    val Comment: ImageVector by lazy {
        outline(
            "comment",
            "M12,3.5C7.31,3.5 3.5,7.08 3.5,11.5c0,1.6 0.5,3.09 1.37,4.34L4,20l4.3,-0.95c1.11,0.54 2.36,0.95 3.7,0.95c4.69,0 8.5,-3.58 8.5,-8.5S16.69,3.5 12,3.5z",
        )
    }

    /** a speaker with sound waves: the video plays with sound */
    val VolumeOn: ImageVector by lazy {
        filled("volume_on", "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z")
    }

    /** a speaker crossed out: the video is muted */
    val VolumeOff: ImageVector by lazy {
        filled("volume_off", "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zM19,12c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27 7.73,9H3v6h4l5,5v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81L19.73,21 21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09 12,8.18V4z")
    }

    /** a box with an arrow leaving it: the post links off reddit */
    val ExternalLink: ImageVector by lazy {
        filled("external_link", "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z")
    }

    // a 24x24 solid icon; Icon's tint sets its color
    private fun filled(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
            .build()

    // a 24x24 icon drawn as a 2px line; Icon's tint sets its color
    private fun outline(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = addPathNodes(pathData),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}
