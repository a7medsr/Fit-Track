package com.example.fittrack

import com.example.fittrack.domain.model.CommunityMetric
import com.example.fittrack.domain.model.DailySteps
import com.example.fittrack.domain.model.Workout
import com.example.fittrack.domain.util.WeeklyScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeeklyScoreCalculatorTest {

    // Wednesday, so the week under test runs Mon 24th to today.
    private val today = LocalDate.of(2026, 8, 26)

    private val steps = listOf(
        DailySteps("2026-08-23", 9_000),   // Sunday, the week before
        DailySteps("2026-08-24", 6_000),
        DailySteps("2026-08-25", 4_000),
        DailySteps("2026-08-26", 2_000),
        DailySteps("2026-08-29", 8_000)    // Saturday, still to come
    )

    private val workouts = listOf(
        workout(date = "2026-08-23", minutes = 60, calories = 500),
        workout(date = "2026-08-24", minutes = 30, calories = 250),
        workout(date = "2026-08-26", minutes = 45, calories = 400)
    )

    @Test
    fun `steps counts only this week up to today`() {
        assertEquals(
            12_000,
            WeeklyScoreCalculator.scoreFor(CommunityMetric.STEPS, steps, workouts, today)
        )
    }

    @Test
    fun `active minutes sums logged workouts in the week`() {
        assertEquals(
            75,
            WeeklyScoreCalculator.scoreFor(CommunityMetric.ACTIVE_MINUTES, steps, workouts, today)
        )
    }

    @Test
    fun `workout count is sessions not minutes`() {
        assertEquals(
            2,
            WeeklyScoreCalculator.scoreFor(CommunityMetric.WORKOUTS, steps, workouts, today)
        )
    }

    /**
     * Calories deliberately include walking. A member whose week was all
     * walking has genuinely burned those, and would otherwise sit on zero.
     */
    @Test
    fun `calories combine workouts and steps`() {
        val expected = 650 + (12_000 * 0.04).toInt()

        assertEquals(
            expected,
            WeeklyScoreCalculator.scoreFor(CommunityMetric.CALORIES, steps, workouts, today)
        )
    }

    @Test
    fun `a day still ahead in the week does not count yet`() {
        val saturdayOnly = listOf(DailySteps("2026-08-29", 8_000))

        assertEquals(
            0,
            WeeklyScoreCalculator.scoreFor(CommunityMetric.STEPS, saturdayOnly, emptyList(), today)
        )
    }

    @Test
    fun `no data scores zero rather than failing`() {
        CommunityMetric.entries.forEach { metric ->
            assertEquals(
                "metric $metric",
                0,
                WeeklyScoreCalculator.scoreFor(metric, emptyList(), emptyList(), today)
            )
        }
    }

    /**
     * Both sources store dates as strings, so one unparseable row must be
     * skipped rather than take the whole score down with it -- a crash here
     * would stop every leaderboard the user is on from updating.
     */
    @Test
    fun `a malformed date is ignored`() {
        val withRubbish = steps + DailySteps("not-a-date", 50_000)

        assertEquals(
            12_000,
            WeeklyScoreCalculator.scoreFor(CommunityMetric.STEPS, withRubbish, workouts, today)
        )
    }

    @Test
    fun `on a Monday only that day counts`() {
        val monday = LocalDate.of(2026, 8, 24)

        assertEquals(
            6_000,
            WeeklyScoreCalculator.scoreFor(CommunityMetric.STEPS, steps, workouts, monday)
        )
    }

    private fun workout(date: String, minutes: Int, calories: Int) = Workout(
        type = "Running",
        durationMinutes = minutes,
        calories = calories,
        date = date
    )
}
