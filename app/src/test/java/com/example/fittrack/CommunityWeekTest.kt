package com.example.fittrack

import com.example.fittrack.domain.util.CommunityWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CommunityWeekTest {

    @Test
    fun `week runs Monday to Sunday`() {
        val wednesday = LocalDate.of(2026, 8, 26)

        assertEquals(LocalDate.of(2026, 8, 24), CommunityWeek.startOf(wednesday))
        assertEquals(LocalDate.of(2026, 8, 30), CommunityWeek.endOf(wednesday))
    }

    @Test
    fun `every day of one week shares an id`() {
        val ids = (24..30)
            .map { CommunityWeek.idFor(LocalDate.of(2026, 8, it)) }
            .distinct()

        assertEquals(1, ids.size)
    }

    @Test
    fun `the next Monday starts a new id`() {
        val sunday = CommunityWeek.idFor(LocalDate.of(2026, 8, 30))
        val monday = CommunityWeek.idFor(LocalDate.of(2026, 8, 31))

        assertTrue(sunday != monday)
    }

    /**
     * The case that makes this worth a test at all. 1 January 2027 is a Friday,
     * so it belongs to the last ISO week of 2026. Building the id from the
     * calendar year instead of the week-based year would file it as 2027-W53
     * and split one week's scores across two boards.
     */
    @Test
    fun `new year in the middle of a week keeps the old week's id`() {
        val newYearsDay = LocalDate.of(2027, 1, 1)
        val theMondayBefore = LocalDate.of(2026, 12, 28)

        assertEquals("2026-W53", CommunityWeek.idFor(newYearsDay))
        assertEquals(
            CommunityWeek.idFor(theMondayBefore),
            CommunityWeek.idFor(newYearsDay)
        )
    }

    @Test
    fun `early January that belongs to the new year is week one`() {
        // 2026-01-01 is a Thursday, so that week is ISO week 1 of 2026.
        assertEquals("2026-W01", CommunityWeek.idFor(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `previous id is the week before`() {
        val date = LocalDate.of(2026, 8, 26)

        assertEquals(
            CommunityWeek.idFor(date.minusWeeks(1)),
            CommunityWeek.previousId(date)
        )
    }

    @Test
    fun `days so far stops at today rather than running to Sunday`() {
        val wednesday = LocalDate.of(2026, 8, 26)
        val days = CommunityWeek.daysSoFar(wednesday)

        assertEquals(3, days.size)
        assertEquals(LocalDate.of(2026, 8, 24), days.first())
        assertEquals(wednesday, days.last())
    }

    @Test
    fun `on a Monday the week is one day long`() {
        val monday = LocalDate.of(2026, 8, 24)

        assertEquals(listOf(monday), CommunityWeek.daysSoFar(monday))
    }

    @Test
    fun `on a Sunday the whole week counts`() {
        assertEquals(7, CommunityWeek.daysSoFar(LocalDate.of(2026, 8, 30)).size)
    }
}
