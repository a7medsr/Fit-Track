package com.example.fittrack

import com.example.fittrack.data.ai.AnswerCachePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerCachePolicyTest {

    /**
     * Regression: the pronoun "I" used to count as a personal reference, so
     * almost nothing was ever cached and the Tier 3 cache never fired.
     */
    @Test
    fun `general questions containing the pronoun I are cacheable`() {
        listOf(
            "how much protein should I eat after training",
            "should I stretch before running",
            "how do I improve my VO2 max".replace("my ", ""), // keep it general
            "is it better to run in the morning"
        ).forEach { assertTrue(it, AnswerCachePolicy.isCacheable(it)) }
    }

    @Test
    fun `questions about the user's own data are never cached`() {
        listOf(
            "how am I doing today",
            "what is my streak",
            "how many steps do I have so far",
            "what did I burn yesterday",
            "am I on track currently"
        ).forEach { assertFalse(it, AnswerCachePolicy.isCacheable(it)) }
    }

    @Test
    fun `normalisation collapses punctuation, case and spacing`() {
        assertEquals(
            AnswerCachePolicy.normalise("Is soreness after lifting normal?"),
            AnswerCachePolicy.normalise("  is   soreness after lifting normal  ")
        )
        assertEquals(
            "how much protein should i eat",
            AnswerCachePolicy.normalise("How much protein should I eat??")
        )
    }

    @Test
    fun `different questions do not collide`() {
        assertTrue(
            AnswerCachePolicy.normalise("what is a superset") !=
                AnswerCachePolicy.normalise("what is progressive overload")
        )
    }
}
