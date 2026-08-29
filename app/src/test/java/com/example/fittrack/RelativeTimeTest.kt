package com.example.fittrack

import com.example.fittrack.ui.common.RelativeTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class RelativeTimeTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `just now`() {
        assertEquals("now", RelativeTime.shortLabel(now - 30_000, now))
    }

    @Test
    fun `minutes then hours then days`() {
        assertEquals("5m", RelativeTime.shortLabel(now - TimeUnit.MINUTES.toMillis(5), now))
        assertEquals("3h", RelativeTime.shortLabel(now - TimeUnit.HOURS.toMillis(3), now))
        assertEquals("2d", RelativeTime.shortLabel(now - TimeUnit.DAYS.toMillis(2), now))
    }

    @Test
    fun `beyond a week it becomes a date rather than a day count`() {
        val label = RelativeTime.shortLabel(now - TimeUnit.DAYS.toMillis(40), now)

        // "63d" tells nobody anything, so the format changes entirely.
        assertEquals(false, label.endsWith("d"))
    }

    /**
     * Two devices never agree on the clock to the second, so a timestamp a
     * little in the future is normal. It must read "now", not a negative age.
     */
    @Test
    fun `a timestamp slightly in the future reads as now`() {
        assertEquals("now", RelativeTime.shortLabel(now + 5_000, now))
    }

    @Test
    fun `a missing timestamp is a dash`() {
        assertEquals("—", RelativeTime.shortLabel(0L, now))
    }

    @Test
    fun `freshness never says updated now ago`() {
        assertEquals("updated just now", RelativeTime.freshness(now - 10_000, now))
    }

    @Test
    fun `freshness explains a stale row`() {
        assertEquals(
            "updated 3d ago",
            RelativeTime.freshness(now - TimeUnit.DAYS.toMillis(3), now)
        )
    }

    @Test
    fun `a member who never published has no score rather than a stale one`() {
        assertEquals("no score yet", RelativeTime.freshness(0L, now))
    }
}
