package com.example.fittrack

import com.example.fittrack.data.ai.LocalIntent
import com.example.fittrack.data.ai.TierClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tier 1 is the whole cost strategy, so these pin down what stays free and,
 * just as importantly, what must never be answered locally.
 */
class TierClassifierTest {

    @Test
    fun `steps remaining is answered locally`() {
        listOf(
            "how many steps left today?",
            "how many more steps do I need",
            "steps remaining",
            "how far am I from my goal in steps"
        ).forEach {
            assertEquals(it, LocalIntent.STEPS_REMAINING, TierClassifier.classify(it))
        }
    }

    @Test
    fun `step count and goal are answered locally`() {
        assertEquals(LocalIntent.STEPS_TODAY, TierClassifier.classify("how many steps today"))
        assertEquals(LocalIntent.CURRENT_GOAL, TierClassifier.classify("what is my step goal"))
    }

    @Test
    fun `streak calories and workouts are answered locally`() {
        assertEquals(LocalIntent.STREAK, TierClassifier.classify("what's my streak?"))
        assertEquals(
            LocalIntent.CALORIES_TODAY,
            TierClassifier.classify("how many calories have I burned today")
        )
        assertEquals(
            LocalIntent.WORKOUTS_TODAY,
            TierClassifier.classify("what workouts did I do today")
        )
    }

    @Test
    fun `writes never route to tier 1 even when they mention stats`() {
        // These read like stats questions but change state; answering them
        // locally would silently do nothing.
        listOf(
            "set my step goal to 12000",
            "change my goal",
            "log a 30 minute run",
            "add push ups to my exercises",
            "update my weight to 80kg"
        ).forEach { assertNull(it, TierClassifier.classify(it)) }
    }

    @Test
    fun `general fitness questions go to the model`() {
        listOf(
            "is soreness after lifting normal?",
            "how much protein should I eat",
            "what is progressive overload",
            "should I stretch before running"
        ).forEach { assertNull(it, TierClassifier.classify(it)) }
    }

    /**
     * Regression: "how much protein should I eat after training" was answered
     * locally as WORKOUTS_TODAY, because "training" read as a metric and "how
     * much" as a personal reference. A wrong free answer is worse than a paid
     * right one, so these all have to reach the model.
     */
    @Test
    fun `fitness words inside advice questions must not trigger a local answer`() {
        listOf(
            "how much protein should I eat after training",
            "how many calories should I eat to lose weight",
            "how many steps a day is healthy",
            "is my form correct when squatting",
            "what is the best exercise for my back",
            "should I train today if I am sore",
            "how many rest days do I need",
            "why is my streak important"
        ).forEach { assertNull(it, TierClassifier.classify(it)) }
    }

    @Test
    fun `personal stat questions still resolve after the tightening`() {
        assertEquals(LocalIntent.STEPS_REMAINING, TierClassifier.classify("steps left today"))
        assertEquals(LocalIntent.CURRENT_GOAL, TierClassifier.classify("what is my step goal"))
        assertEquals(LocalIntent.STREAK, TierClassifier.classify("what is my streak"))
        assertEquals(
            LocalIntent.CALORIES_TODAY,
            TierClassifier.classify("how many calories have I burned today")
        )
        assertEquals(
            LocalIntent.WORKOUTS_TODAY,
            TierClassifier.classify("what workouts did I do today")
        )
        assertEquals(LocalIntent.BODY_WEIGHT, TierClassifier.classify("what is my weight"))
    }

    @Test
    fun `a question with no personal reference goes to the model`() {
        // No "my", no "today": this is a general question about steps.
        assertNull(TierClassifier.classify("are steps a good measure of fitness"))
    }

    @Test
    fun `blank input goes to the model rather than guessing`() {
        assertNull(TierClassifier.classify(""))
        assertNull(TierClassifier.classify("   "))
    }
}
