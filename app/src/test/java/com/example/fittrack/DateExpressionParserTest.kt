package com.example.fittrack

import com.example.fittrack.data.ai.DateExpressionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateExpressionParserTest {

    private val today = LocalDate.of(2026, 8, 28) // Friday

    private fun start(text: String) = DateExpressionParser.parse(text, today)?.start

    @Test
    fun `iso dates parse`() {
        assertEquals(LocalDate.of(2026, 8, 25), start("what did I do 2026-08-25"))
        assertEquals(LocalDate.of(2026, 8, 25), start("what did I do 2026-8-25"))
    }

    /** The exact form from the bug report. */
    @Test
    fun `day-month shorthand parses`() {
        assertEquals(LocalDate.of(2026, 8, 25), start("what is the actives i did 25-8"))
        assertEquals(LocalDate.of(2026, 8, 25), start("steps on 25/8"))
        assertEquals(LocalDate.of(2026, 8, 25), start("steps on 25-08-2026"))
    }

    @Test
    fun `an unambiguous month-day order is understood too`() {
        // 25 cannot be a month, so this is August 25 however it is written.
        assertEquals(LocalDate.of(2026, 8, 25), start("steps on 8-25"))
    }

    @Test
    fun `month names parse in both orders`() {
        assertEquals(LocalDate.of(2026, 8, 25), start("what did I do on august 25"))
        assertEquals(LocalDate.of(2026, 8, 25), start("what did I do on 25 august"))
        assertEquals(LocalDate.of(2026, 8, 25), start("what did I do on aug 25"))
        assertEquals(LocalDate.of(2026, 8, 25), start("what did I do on 25th of august"))
    }

    @Test
    fun `a date with no year means the most recent one that already happened`() {
        // December has not arrived in August 2026, so it means December 2025.
        assertEquals(LocalDate.of(2025, 12, 25), start("what did I do on 25 december"))
    }

    @Test
    fun `relative days parse`() {
        assertEquals(today, start("how many steps today"))
        assertEquals(today.minusDays(1), start("how many steps yesterday"))
        assertEquals(today.minusDays(3), start("what did I do 3 days ago"))
    }

    @Test
    fun `weekdays resolve to the most recent past occurrence`() {
        // Today is Friday; "last monday" is the 24th.
        assertEquals(LocalDate.of(2026, 8, 24), start("what did I do last monday"))
    }

    @Test
    fun `week and month ranges span the right days`() {
        val week = DateExpressionParser.parse("steps this week", today)!!
        assertEquals(LocalDate.of(2026, 8, 24), week.start)
        assertEquals(today, week.end)
        assertTrue(!week.isSingleDay)

        val lastWeek = DateExpressionParser.parse("steps last week", today)!!
        assertEquals(LocalDate.of(2026, 8, 17), lastWeek.start)
        assertEquals(LocalDate.of(2026, 8, 23), lastWeek.end)

        val month = DateExpressionParser.parse("steps this month", today)!!
        assertEquals(LocalDate.of(2026, 8, 1), month.start)

        val sevenDays = DateExpressionParser.parse("steps in the last 7 days", today)!!
        assertEquals(today.minusDays(6), sevenDays.start)
        assertEquals(7, sevenDays.dayCount)
    }

    @Test
    fun `last N days is honoured for arbitrary N`() {
        val period = DateExpressionParser.parse("steps in the last 14 days", today)!!
        assertEquals(today.minusDays(13), period.start)
        assertEquals(14, period.dayCount)
    }

    @Test
    fun `text with no date returns null so the caller can default to today`() {
        assertNull(DateExpressionParser.parse("how many steps", today))
        assertNull(DateExpressionParser.parse("what is my streak", today))
    }

    @Test
    fun `ranges win over a bare weekday inside them`() {
        // "this week" must not be swallowed by some weekday match.
        val period = DateExpressionParser.parse("how many steps this week", today)!!
        assertTrue(!period.isSingleDay)
    }

    @Test
    fun `labels name the period back to the user`() {
        assertEquals("today", DateExpressionParser.parse("steps today", today)?.label)
        assertEquals("yesterday", DateExpressionParser.parse("steps yesterday", today)?.label)
        assertEquals("this week", DateExpressionParser.parse("steps this week", today)?.label)
        assertTrue(
            DateExpressionParser.parse("steps on 25-8", today)?.label?.contains("25") == true
        )
    }
}
