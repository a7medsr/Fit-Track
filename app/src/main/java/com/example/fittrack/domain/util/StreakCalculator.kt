package com.example.fittrack.domain.util

import java.time.LocalDate

/**
 * Consecutive-day streaks over the set of days the user was active.
 *
 * Shared by the Charts and Records screens so the two can never disagree about
 * what the current streak is.
 */
object StreakCalculator {

    /**
     * Length of the run ending today.
     *
     * Today is allowed to be missing: at 9am you have not moved yet, and
     * breaking the streak until the first steps arrive would be wrong. An
     * unbroken run ending yesterday therefore still counts as current.
     */
    fun current(activeDates: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int {
        if (activeDates.isEmpty()) return 0
        var cursor = if (today in activeDates) today else today.minusDays(1)
        var count = 0
        while (cursor in activeDates) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** Longest run anywhere in the history. */
    fun longest(activeDates: Set<LocalDate>): Int {
        if (activeDates.isEmpty()) return 0
        val sorted = activeDates.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }
}
