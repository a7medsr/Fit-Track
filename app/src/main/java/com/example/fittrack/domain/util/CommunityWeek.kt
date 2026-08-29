package com.example.fittrack.domain.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

/**
 * The Monday-to-Sunday week a leaderboard runs on.
 *
 * The week is worked out from each device's own local date, so two members in
 * different time zones can briefly disagree about when the week turned over.
 * That is accepted: the alternative is pinning every community to one zone,
 * which makes the board wrong for everybody who is not in it.
 */
object CommunityWeek {

    private val ISO = WeekFields.ISO

    /**
     * A sortable id such as `2026-W35`, used as the key half of a score
     * document and as the thing an old week is recognised by.
     *
     * Deliberately built from the *week-based* year rather than the calendar
     * year. On 1 January 2027, a Friday, the ISO week is week 53 of 2026 -- and
     * using `getYear()` there would file it as `2027-W53`, splitting one week's
     * scores across two ids and losing half the board.
     */
    fun idFor(date: LocalDate = LocalDate.now()): String {
        val weekYear = date.get(ISO.weekBasedYear())
        val week = date.get(ISO.weekOfWeekBasedYear())
        return "%04d-W%02d".format(weekYear, week)
    }

    fun previousId(date: LocalDate = LocalDate.now()): String = idFor(date.minusWeeks(1))

    fun startOf(date: LocalDate = LocalDate.now()): LocalDate =
        date.with(ISO.dayOfWeek(), 1)

    fun endOf(date: LocalDate = LocalDate.now()): LocalDate =
        date.with(ISO.dayOfWeek(), 7)

    /** The set of dates in this week up to and including today, never beyond. */
    fun daysSoFar(today: LocalDate = LocalDate.now()): List<LocalDate> {
        val start = startOf(today)
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .toList()
    }

    /** Something like `25 Aug – 31 Aug`, for the board's header. */
    fun label(date: LocalDate = LocalDate.now()): String {
        val start = startOf(date)
        val end = endOf(date)
        val day = DateTimeFormatter.ofPattern("d MMM")
        return "${start.format(day)} – ${end.format(day)}"
    }
}
