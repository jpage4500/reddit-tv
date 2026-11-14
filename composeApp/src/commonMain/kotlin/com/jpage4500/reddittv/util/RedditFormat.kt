package com.jpage4500.reddittv.util

import kotlin.math.roundToInt

/**
 * the short numbers and ages reddit shows: "12.3k", "4h"
 */
object RedditFormat {
    fun count(n: Int): String = when {
        n >= 1_000_000 -> oneDecimal(n / 1_000_000.0) + "m"
        n >= 10_000 -> "${(n / 1000.0).roundToInt()}k"
        n >= 1_000 -> oneDecimal(n / 1000.0) + "k"
        else -> n.toString()
    }

    /** [createdUtc] in seconds, as reddit gives it */
    fun age(createdUtc: Long): String {
        if (createdUtc <= 0) return ""
        val secs = DateUtils.nowMs() / 1000 - createdUtc
        return when {
            secs < 60 -> "now"
            secs < 3600 -> "${secs / 60}m"
            secs < 86_400 -> "${secs / 3600}h"
            secs < 86_400 * 30 -> "${secs / 86_400}d"
            secs < 86_400 * 365 -> "${secs / (86_400 * 30)}mo"
            else -> "${secs / (86_400 * 365)}y"
        }
    }

    /** [text] on one line, for a preview: line breaks and runs of spaces become one space */
    fun oneLine(text: String): String = text.replace(WHITESPACE, " ").trim()

    private val WHITESPACE = Regex("\\s+")

    private fun oneDecimal(v: Double): String {
        val tenths = (v * 10).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }
}
