package com.example.fittrack.ui.common

import java.util.concurrent.TimeUnit

/**
 * Short "how long ago" labels for feeds and leaderboards.
 *
 * Deliberately coarse. A post from four minutes ago says "4m", not "4 minutes
 * and 12 seconds", and anything older than a week gets a date instead, because
 * "63d" tells nobody anything.
 */
object RelativeTime {

    fun shortLabel(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return "—"
        val elapsed = now - timestamp
        // A clock skewed a little into the future should read "now", not a
        // negative age.
        if (elapsed < TimeUnit.MINUTES.toMillis(1)) return "now"

        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
        if (minutes < 60) return "${minutes}m"

        val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
        if (hours < 24) return "${hours}h"

        val days = TimeUnit.MILLISECONDS.toDays(elapsed)
        if (days < 7) return "${days}d"

        return java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }

    /**
     * "updated 3d ago", explaining why a member's number looks stalled.
     *
     * The very recent case is special-cased because the label it would
     * otherwise compose is "updated now ago".
     */
    fun freshness(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return "no score yet"
        val label = shortLabel(timestamp, now)
        return if (label == "now") "updated just now" else "updated $label ago"
    }
}
