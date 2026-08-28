package com.example.fittrack.data.ai

/**
 * Decides whether a Tier 3 answer is safe to reuse.
 *
 * Only general knowledge is cached. An answer that leaned on the user's own
 * numbers goes stale the moment they take another step, so anything asking
 * about *their* data is excluded.
 *
 * The bar is possessive and temporal references -- "my", "today" -- not the
 * pronoun "I". Almost every natural question contains "I" ("how much protein
 * should I eat"), and treating that as personal made the cache never fire,
 * which is the whole point of having one.
 */
object AnswerCachePolicy {

    fun isCacheable(question: String): Boolean =
        !PERSONAL_REFERENCE.containsMatchIn(question.lowercase())

    /** Normalised so trivial rewordings share a cache entry. */
    fun normalise(question: String): String =
        question.lowercase()
            .filter { it.isLetterOrDigit() || it == ' ' }
            .replace(Regex("\\s+"), " ")
            .trim()

    private val PERSONAL_REFERENCE = Regex(
        "\\b(my|mine|today|yesterday|tonight|this week|this month|so far|" +
            "currently|current|right now|my goal|streak)\\b"
    )
}
