package com.example.fittrack.data.ai

/**
 * Questions the app can answer from its own database, with no network call at
 * all. This is Tier 1 of the routing and it is the whole cost strategy: the
 * questions people actually ask an in-app assistant are overwhelmingly "how far
 * am I today", and none of those need a model.
 */
enum class LocalIntent {
    STEPS_REMAINING,
    STEPS_TODAY,
    CURRENT_GOAL,
    STREAK,
    CALORIES_TODAY,
    WORKOUTS_TODAY,
    BODY_WEIGHT,
    HELP
}

/**
 * Picks the cheapest tier that can serve a message.
 *
 * Returns a [LocalIntent] for Tier 1, or null to hand the message to the model,
 * which then decides between an action (Tier 2) and general knowledge (Tier 3).
 *
 * The bar for Tier 1 is deliberately high. A false negative costs one API call;
 * a false positive answers a completely different question and sounds certain
 * doing it -- "how much protein should I eat after training" must never come
 * back as "nothing logged today". A message therefore has to clear three gates:
 * it must not be an instruction, must not read as general knowledge, and must
 * pair a tracked metric with a reference to this user or to today.
 */
object TierClassifier {

    fun classify(rawMessage: String): LocalIntent? {
        val text = rawMessage.lowercase().trim()
        if (text.isEmpty()) return null

        if (HELP.containsMatchIn(text)) return LocalIntent.HELP

        // Gate 1: an imperative is a write, however much it looks like a question.
        if (WRITE_VERBS.containsMatchIn(text)) return null

        // Gate 2: advice, definitions and nutrition are the model's job even
        // when they mention steps, calories or training.
        if (GENERAL_KNOWLEDGE.containsMatchIn(text)) return null

        // Gate 3: must actually be about this user's own numbers. Asking what
        // is "left" or "how many more" is inherently about own progress, so it
        // satisfies this gate on its own.
        if (!PERSONAL.containsMatchIn(text) && !REMAINING.containsMatchIn(text)) return null

        return when {
            STEPS.containsMatchIn(text) && REMAINING.containsMatchIn(text) ->
                LocalIntent.STEPS_REMAINING

            GOAL.containsMatchIn(text) && !REMAINING.containsMatchIn(text) ->
                LocalIntent.CURRENT_GOAL

            STEPS.containsMatchIn(text) -> LocalIntent.STEPS_TODAY

            STREAK.containsMatchIn(text) -> LocalIntent.STREAK

            CALORIES.containsMatchIn(text) -> LocalIntent.CALORIES_TODAY

            WORKOUTS.containsMatchIn(text) -> LocalIntent.WORKOUTS_TODAY

            WEIGHT.containsMatchIn(text) -> LocalIntent.BODY_WEIGHT

            else -> null
        }
    }

    private val WRITE_VERBS =
        Regex("\\b(set|change|update|make|log|add|create|record|delete|remove|rename)\\b")

    /**
     * Any of these turns the message into a knowledge question. "should" and
     * "eat" are the load-bearing ones: they separate "how many calories today"
     * from "how many calories should I eat".
     */
    private val GENERAL_KNOWLEDGE = Regex(
        "\\b(should|why|explain|benefits?|best|better|recommend|advice|tips?|normal|safe|" +
            "protein|diet|nutrition|nutrient|supplement|carbs?|meal|eat|eating|drink|hydrat|" +
            "sleep|injur|sore|soreness|stretch|warm ?up|cool ?down|form|technique|posture|" +
            "how to|how do i|what does|difference between|good for|bad for|help with|mean)\\b"
    )

    /** The question has to be about this user, not about fitness in general. */
    private val PERSONAL = Regex(
        "\\b(my|mine|today|so far|currently|current|left|remaining|to go|" +
            "did i|have i|do i have|i've|ive|am i)\\b"
    )

    private val HELP = Regex("\\b(what can you do|help me|how do i use|what do you do)\\b")
    private val STEPS = Regex("\\bsteps?\\b")
    private val REMAINING =
        Regex("\\b(remaining|left|to go|how many more|still need|short of|until|how far)\\b")
    private val GOAL = Regex("\\b(goal|target)\\b")
    private val STREAK = Regex("\\bstreak\\b")
    private val CALORIES = Regex("\\b(calorie|calories|kcal|burned|burnt)\\b")

    /**
     * "training" and a bare "exercise" are deliberately absent: they appear far
     * more often in general questions than in "what did I log today".
     */
    private val WORKOUTS = Regex("\\b(workouts?|sessions?|exercises)\\b")
    private val WEIGHT = Regex("\\b(weigh|weight|kg)\\b")
}
