package com.example.fittrack.data.ai

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * A span of days a question is asking about, inclusive at both ends.
 *
 * [label] is how the answer refers to it back to the user ("on Tue 25 Aug",
 * "this week"), so the reply always names the period it actually measured.
 */
data class DatePeriod(
    val start: LocalDate,
    val end: LocalDate,
    val label: String,
    val isSingleDay: Boolean
) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    val dayCount: Int
        get() = (java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1)
            .coerceAtLeast(1)

    companion object {
        fun day(date: LocalDate, label: String) = DatePeriod(date, date, label, true)
    }
}

/**
 * Turns the date part of a question into a [DatePeriod].
 *
 * Handles what people actually type: "yesterday", "25-8", "2026-08-25",
 * "august 25", "last monday", "3 days ago", "this week", "last month".
 *
 * Returns null when the text names no period at all, which the caller reads as
 * "today" for a stats question.
 */
object DateExpressionParser {

    fun parse(rawText: String, today: LocalDate = LocalDate.now()): DatePeriod? {
        val text = rawText.lowercase()

        // Ranges first: "this week" must not be eaten by a bare weekday match.
        parseRange(text, today)?.let { return it }
        parseRelativeDay(text, today)?.let { return it }
        parseIsoDate(text)?.let { return DatePeriod.day(it, describe(it, today)) }
        parseNumericDate(text, today)?.let { return DatePeriod.day(it, describe(it, today)) }
        parseMonthNameDate(text, today)?.let { return DatePeriod.day(it, describe(it, today)) }
        parseWeekday(text, today)?.let { return DatePeriod.day(it, describe(it, today)) }
        return null
    }

    fun todayPeriod(today: LocalDate = LocalDate.now()): DatePeriod =
        DatePeriod.day(today, "today")

    // ------------------------------------------------------------- ranges

    private fun parseRange(text: String, today: LocalDate): DatePeriod? {
        LAST_N_DAYS.find(text)?.let { match ->
            val n = match.groupValues[1].toIntOrNull() ?: return@let
            if (n in 2..400) {
                return DatePeriod(today.minusDays(n - 1L), today, "the last $n days", false)
            }
        }

        return when {
            text.contains("all time") || text.contains("ever") || text.contains("in total") ||
                text.contains("overall") || text.contains("altogether") ->
                DatePeriod(LocalDate.of(2000, 1, 1), today, "all time", false)

            text.contains("this week") ->
                DatePeriod(today.with(DayOfWeek.MONDAY), today, "this week", false)

            text.contains("last week") || text.contains("previous week") -> {
                val start = today.with(DayOfWeek.MONDAY).minusWeeks(1)
                DatePeriod(start, start.plusDays(6), "last week", false)
            }

            text.contains("past week") || text.contains("last 7 days") ->
                DatePeriod(today.minusDays(6), today, "the last 7 days", false)

            text.contains("this month") ->
                DatePeriod(today.withDayOfMonth(1), today, "this month", false)

            text.contains("last month") || text.contains("previous month") -> {
                val start = today.withDayOfMonth(1).minusMonths(1)
                DatePeriod(start, start.plusMonths(1).minusDays(1), "last month", false)
            }

            text.contains("past month") || text.contains("last 30 days") ->
                DatePeriod(today.minusDays(29), today, "the last 30 days", false)

            text.contains("this year") ->
                DatePeriod(today.withDayOfYear(1), today, "this year", false)

            else -> null
        }
    }

    // -------------------------------------------------------- single days

    private fun parseRelativeDay(text: String, today: LocalDate): DatePeriod? = when {
        text.contains("today") || text.contains("so far") || text.contains("right now") ->
            DatePeriod.day(today, "today")

        text.contains("yesterday") ->
            DatePeriod.day(today.minusDays(1), "yesterday")

        text.contains("day before yesterday") ->
            DatePeriod.day(today.minusDays(2), "the day before yesterday")

        else -> DAYS_AGO.find(text)?.let { match ->
            val n = match.groupValues[1].toIntOrNull() ?: return@let null
            if (n !in 1..400) return@let null
            val date = today.minusDays(n.toLong())
            DatePeriod.day(date, describe(date, today))
        }
    }

    /** 2026-08-25, and the same with slashes. */
    private fun parseIsoDate(text: String): LocalDate? =
        ISO_DATE.find(text)?.let { match ->
            runCatching {
                LocalDate.of(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt()
                )
            }.getOrNull()
        }

    /**
     * "25-8", "25/8/2026", "8-25".
     *
     * Ambiguity is resolved by whichever number cannot be a month; when both
     * could be (3-4), day-month is assumed, which is what most of the world
     * writes and what the app's own dd MMM formatting suggests.
     */
    private fun parseNumericDate(text: String, today: LocalDate): LocalDate? {
        val match = NUMERIC_DATE.find(text) ?: return null
        val a = match.groupValues[1].toIntOrNull() ?: return null
        val b = match.groupValues[2].toIntOrNull() ?: return null
        val explicitYear = match.groupValues[3].toIntOrNull()?.let {
            if (it < 100) 2000 + it else it
        }

        val (day, month) = when {
            a > 12 && b <= 12 -> a to b
            b > 12 && a <= 12 -> b to a
            a <= 12 && b <= 12 -> a to b // both plausible: day-month
            else -> return null
        }
        if (month !in 1..12 || day !in 1..31) return null

        return buildDate(day, month, explicitYear, today)
    }

    /** "august 25", "25 august", "aug 25". */
    private fun parseMonthNameDate(text: String, today: LocalDate): LocalDate? {
        MONTH_THEN_DAY.find(text)?.let { match ->
            val month = monthNumber(match.groupValues[1]) ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            val year = match.groupValues[3].toIntOrNull()
            return buildDate(day, month, year, today)
        }
        DAY_THEN_MONTH.find(text)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let
            val month = monthNumber(match.groupValues[2]) ?: return@let
            val year = match.groupValues[3].toIntOrNull()
            return buildDate(day, month, year, today)
        }
        return null
    }

    /** "last monday", or a bare "monday" meaning the most recent one. */
    private fun parseWeekday(text: String, today: LocalDate): LocalDate? {
        val match = WEEKDAY.find(text) ?: return null
        val target = when (match.groupValues[2]) {
            "monday", "mon" -> DayOfWeek.MONDAY
            "tuesday", "tue", "tues" -> DayOfWeek.TUESDAY
            "wednesday", "wed" -> DayOfWeek.WEDNESDAY
            "thursday", "thu", "thur", "thurs" -> DayOfWeek.THURSDAY
            "friday", "fri" -> DayOfWeek.FRIDAY
            "saturday", "sat" -> DayOfWeek.SATURDAY
            "sunday", "sun" -> DayOfWeek.SUNDAY
            else -> return null
        }
        var date = today
        do {
            date = date.minusDays(1)
        } while (date.dayOfWeek != target)
        return date
    }

    /**
     * A date with no year means the most recent one that has already happened;
     * "25-8" in January refers to last August, not a date months away.
     */
    private fun buildDate(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? {
        if (year != null) return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
            ?: return null
        return if (thisYear.isAfter(today)) thisYear.minusYears(1) else thisYear
    }

    private fun monthNumber(name: String): Int? = MONTHS[name.take(3)]

    private fun describe(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> {
            val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val month = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            "on $dow ${date.dayOfMonth} $month"
        }
    }

    private val ISO_DATE = Regex("\\b(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})\\b")
    private val NUMERIC_DATE = Regex("\\b(\\d{1,2})[-/](\\d{1,2})(?:[-/](\\d{2,4}))?\\b")
    private val DAYS_AGO = Regex("\\b(\\d{1,3})\\s*days?\\s*ago\\b")
    private val LAST_N_DAYS = Regex("\\b(?:last|past)\\s*(\\d{1,3})\\s*days?\\b")
    private val WEEKDAY = Regex(
        "\\b(last\\s+|on\\s+)?(monday|mon|tuesday|tues|tue|wednesday|wed|" +
            "thursday|thurs|thur|thu|friday|fri|saturday|sat|sunday|sun)\\b"
    )
    private val MONTH_THEN_DAY = Regex(
        "\\b(jan\\w*|feb\\w*|mar\\w*|apr\\w*|may|jun\\w*|jul\\w*|aug\\w*|" +
            "sep\\w*|oct\\w*|nov\\w*|dec\\w*)\\s+(\\d{1,2})(?:\\w{0,2})?(?:,?\\s*(\\d{4}))?\\b"
    )
    private val DAY_THEN_MONTH = Regex(
        "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(jan\\w*|feb\\w*|mar\\w*|apr\\w*|may|" +
            "jun\\w*|jul\\w*|aug\\w*|sep\\w*|oct\\w*|nov\\w*|dec\\w*)(?:,?\\s*(\\d{4}))?\\b"
    )
    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )
}
