package com.example.fittrack

import com.example.fittrack.domain.util.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 8, 25)

    private fun daysBack(vararg offsets: Long): Set<LocalDate> =
        offsets.map { today.minusDays(it) }.toSet()

    @Test
    fun `no activity means no streak`() {
        assertEquals(0, StreakCalculator.current(emptySet(), today))
        assertEquals(0, StreakCalculator.longest(emptySet()))
    }

    @Test
    fun `counts an unbroken run ending today`() {
        assertEquals(4, StreakCalculator.current(daysBack(0, 1, 2, 3), today))
    }

    @Test
    fun `a gap ends the current streak`() {
        // Active today and yesterday, then a missing day two back.
        assertEquals(2, StreakCalculator.current(daysBack(0, 1, 3, 4), today))
    }

    @Test
    fun `today not logged yet still counts the run ending yesterday`() {
        // 9am, no steps yet: the streak has not been broken.
        assertEquals(3, StreakCalculator.current(daysBack(1, 2, 3), today))
    }

    @Test
    fun `streak is broken once a full day is missed`() {
        // Nothing today and nothing yesterday.
        assertEquals(0, StreakCalculator.current(daysBack(2, 3, 4), today))
    }

    @Test
    fun `longest finds the best run anywhere in the history`() {
        // A run of 5 well in the past, a run of 2 recently.
        assertEquals(5, StreakCalculator.longest(daysBack(0, 1, 10, 11, 12, 13, 14)))
    }

    @Test
    fun `longest handles a single active day`() {
        assertEquals(1, StreakCalculator.longest(daysBack(7)))
    }

    @Test
    fun `unsorted input is handled`() {
        assertEquals(3, StreakCalculator.longest(daysBack(12, 10, 11)))
    }
}
