package com.example.fittrack.data.ai

/** What the user is asking about. */
enum class Metric {
    STEPS,
    CALORIES,
    WORKOUTS,
    ACTIVE_MINUTES,
    GOAL,
    WEIGHT,
    STREAK,
    /** "what did I do on X" -- everything logged for the period. */
    SUMMARY,
    FAVOURITES,
    CUSTOM_EXERCISES,
    ROUTINES,
    RECORDS,
    HELP
}

/** How the metric should be reduced over the period. */
enum class Aggregate {
    /** Sensible default for the metric and period. */
    AUTO,
    TOTAL,
    AVERAGE,
    BEST,
    COUNT,
    REMAINING
}

/**
 * A question the app can answer from its own database.
 *
 * Splitting it into metric x period x aggregate is what makes Tier 1 cover a
 * realistic range of phrasings: seven metrics times a dozen periods is far more
 * ground than one regex per question could hold, and each part is testable on
 * its own.
 */
data class LocalQuery(
    val metric: Metric,
    val period: DatePeriod,
    val aggregate: Aggregate = Aggregate.AUTO
)
