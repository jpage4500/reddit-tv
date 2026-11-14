package com.jpage4500.reddittv.data

import kotlinx.serialization.Serializable

/**
 * simple rectangle data class for window positioning
 */
@Serializable
data class Rect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

