package com.example.fittrack.data.ai

import java.time.LocalDate

/**
 * Decides whether a message can be answered from the app's own database.
 *
 * Returns a [LocalQuery] for Tier 1 -- free, offline, instant -- or null to
 * hand the message to the model, which then chooses between an action (Tier 2)
 * and general knowledge (Tier 3).
 *
 * The bar for Tier 1 is deliberately high. A false negative costs one API call;
 * a false positive answers a completely different question and sounds certain
 * doing it -- "how much protein should I eat after training" must never come
 * back as "nothing logged today". Every message therefore clears three gates
 * before a metric is even looked for.
 */
object TierClassifier {

    fun classify(rawMessage: String, today: LocalDate = LocalDate.now()): LocalQuery? {
        val text = rawMessage.lowercase().trim()
        if (text.isEmpty()) return null

        if (HELP.containsMatchIn(text)) {
            return LocalQuery(Metric.HELP, DateExpressionParser.todayPeriod(today))
        }

        // Gate 1: an imperative is a write, however much it looks like a
        // question. Past-tense interrogatives are exempt -- "what did I log on
        // 25-8" asks about history, it does not ask to log anything.
        val asksAboutThePast = PAST_QUESTION.containsMatchIn(text)
        if (!asksAboutThePast && WRITE_VERBS.containsMatchIn(text)) return null

        // Gate 2: advice, definitions and nutrition are the model's job even
        // when they mention steps, calories or training.
        if (GENERAL_KNOWLEDGE.containsMatchIn(text)) return null

        val explicitPeriod = DateExpressionParser.parse(text, today)

        // Gate 3: must be about this user's own data. A concrete date, or a
        // word like "remaining", is itself proof of that; otherwise the message
        // has to say "my", "today", "did i" and so on.
        val personal = explicitPeriod != null ||
            PERSONAL.containsMatchIn(text) ||
            REMAINING.containsMatchIn(text)
        if (!personal) return null

        val period = explicitPeriod ?: DateExpressionParser.todayPeriod(today)
        val aggregate = parseAggregate(text, period)

        val metric = parseMetric(text, period) ?: return null
        return LocalQuery(metric, period, aggregate)
    }

    private fun parseMetric(text: String, period: DatePeriod): Metric? = when {
        RECORDS.containsMatchIn(text) -> Metric.RECORDS
        STREAK.containsMatchIn(text) -> Metric.STREAK
        FAVOURITES.containsMatchIn(text) -> Metric.FAVOURITES
        CUSTOM_EXERCISES.containsMatchIn(text) -> Metric.CUSTOM_EXERCISES
        ROUTINES.containsMatchIn(text) -> Metric.ROUTINES

        // Goal only when no explicit past period: "my goal last week" is not a
        // thing the app stores, but "what is my goal" is.
        GOAL.containsMatchIn(text) && !REMAINING.containsMatchIn(text) -> Metric.GOAL

        STEPS.containsMatchIn(text) -> Metric.STEPS
        CALORIES.containsMatchIn(text) -> Metric.CALORIES
        MINUTES.containsMatchIn(text) -> Metric.ACTIVE_MINUTES
        ACTIVITIES.containsMatchIn(text) -> Metric.WORKOUTS
        WEIGHT.containsMatchIn(text) -> Metric.WEIGHT

        // "what did I do on 25-8" names no metric, but a date plus a doing verb
        // is unambiguous enough to summarise the whole day.
        DID_I_DO.containsMatchIn(text) -> Metric.SUMMARY

        else -> null
    }

    private fun parseAggregate(text: String, period: DatePeriod): Aggregate = when {
        REMAINING.containsMatchIn(text) -> Aggregate.REMAINING
        AVERAGE.containsMatchIn(text) -> Aggregate.AVERAGE
        BEST.containsMatchIn(text) -> Aggregate.BEST
        COUNT.containsMatchIn(text) && !period.isSingleDay -> Aggregate.COUNT
        TOTAL.containsMatchIn(text) -> Aggregate.TOTAL
        else -> Aggregate.AUTO
    }

    private val WRITE_VERBS =
        Regex("\\b(set|change|update|make|log|add|create|record|delete|remove|rename)\\b")

    /**
     * Any of these turns the message into a knowledge question. "should" and
     * "eat" are the load-bearing ones: they separate "how many calories today"
     * from "how many calories should I eat".
     */
    private val GENERAL_KNOWLEDGE = Regex(
        "\\b(should|why|explain|benefits?|recommend|advice|tips?|safe|" +
            "protein|diet|nutrition|nutrient|supplement|carbs?|meal|eat|eating|drink|hydrat|" +
            "sleep|injur|sore|soreness|stretch|warm ?up|cool ?down|technique|posture|" +
            "how to|how do i|what does|difference between|good for|bad for|help with)\\b"
    )

    /** The question has to be about this user, not about fitness in general. */
    private val PERSONAL = Regex(
        "\\b(my|mine|today|so far|currently|current|left|remaining|to go|" +
            "did i|have i|do i have|i did|i've|ive|am i|was i)\\b"
    )

    private val HELP = Regex("\\b(what can you do|help me|how do i use|what do you do)\\b")

    private val STEPS = Regex("\\bsteps?\\b")
    private val REMAINING =
        Regex("\\b(remaining|left|to go|how many more|still need|short of|until|how far)\\b")
    private val GOAL = Regex("\\b(goal|target)\\b")
    private val STREAK = Regex("\\bstreak\\b")
    private val CALORIES = Regex("\\b(calorie|calories|kcal|burn|burned|burnt)\\b")
    private val MINUTES = Regex("\\b(minutes?|mins?|active time|how long)\\b")

    /**
     * Deliberately broad, because it is only reached after the three gates.
     * "actives" is here because it is a common typo for "activities".
     */
    private val ACTIVITIES = Regex(
        "\\b(workouts?|work(?:ed|ing)?\\s+out|sessions?|exercises|" +
            "activit\\w*|actives?|trainings?)\\b"
    )

    /** A question about what already happened, not an instruction. */
    private val PAST_QUESTION =
        Regex("\\b(did i|have i|had i|was i|what did i|when did i)\\b")

    private val WEIGHT = Regex("\\b(weigh|weight|kg)\\b")
    private val FAVOURITES = Regex("\\b(favou?rites?|starred|pinned)\\b")
    private val CUSTOM_EXERCISES = Regex("\\b(custom|my own)\\b.*\\bexercis")
    private val ROUTINES = Regex("\\b(routines?|gym sessions?|push day|pull day|leg day|my sessions)\\b")
    private val RECORDS = Regex("\\b(records?|personal best|best ever|pb|all.?time best)\\b")

    private val DID_I_DO = Regex("\\b(did i do|i did|what did i|did i log|was i active|how did i do)\\b")

    private val AVERAGE = Regex("\\b(average|avg|mean|typical|per day)\\b")
    private val BEST = Regex("\\b(best|most|highest|max|maximum|record)\\b")
    private val COUNT = Regex("\\b(how many times|how often|number of|count)\\b")
    private val TOTAL = Regex("\\b(total|altogether|in all|combined|sum)\\b")
}
