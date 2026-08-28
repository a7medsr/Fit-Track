package com.example.fittrack

import com.example.fittrack.data.ai.Aggregate
import com.example.fittrack.data.ai.Metric
import com.example.fittrack.data.ai.TierClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tier 1 is the whole cost strategy, so these pin down what stays free and,
 * just as importantly, what must never be answered locally.
 */
class TierClassifierTest {

    private val today = LocalDate.of(2026, 8, 28) // a Friday

    private fun metric(text: String) = TierClassifier.classify(text, today)?.metric
    private fun query(text: String) = TierClassifier.classify(text, today)

    // ------------------------------------------------------------- today

    @Test
    fun `steps remaining is answered locally`() {
        listOf(
            "how many steps left today?",
            "how many more steps do I need",
            "steps remaining",
            "how far am I from my goal in steps"
        ).forEach {
            assertEquals(it, Metric.STEPS, metric(it))
            assertEquals(it, Aggregate.REMAINING, query(it)?.aggregate)
        }
    }

    @Test
    fun `core today metrics resolve`() {
        assertEquals(Metric.STEPS, metric("how many steps today"))
        assertEquals(Metric.GOAL, metric("what is my step goal"))
        assertEquals(Metric.STREAK, metric("what's my streak?"))
        assertEquals(Metric.CALORIES, metric("how many calories have I burned today"))
        assertEquals(Metric.WORKOUTS, metric("what workouts did I do today"))
        assertEquals(Metric.WEIGHT, metric("what is my weight"))
    }

    // -------------------------------------------------------- past dates

    /**
     * Regression: "What is the Actives i did 25-8" used to reach the model,
     * which has no history in its context and answered "I don't have the
     * activity data for August 25th". The app has that data locally.
     */
    @Test
    fun `activities on an explicit past date are answered locally`() {
        listOf(
            "what is the actives i did 25-8",
            "what is the actives i did 2026-8-25",
            "what activities did I do on august 25",
            "what did I do yesterday",
            "what workouts did I log on 25/8"
        ).forEach {
            assertTrue("$it -> ${query(it)}", query(it) != null)
        }
    }

    @Test
    fun `a past date resolves to that exact day`() {
        val q = query("what is the actives i did 25-8")
        assertEquals(LocalDate.of(2026, 8, 25), q?.period?.start)
        assertEquals(LocalDate.of(2026, 8, 25), q?.period?.end)
        assertTrue(q?.period?.isSingleDay == true)
    }

    @Test
    fun `steps on a past date route to the steps metric`() {
        val q = query("how many steps did I do on 25-8")
        assertEquals(Metric.STEPS, q?.metric)
        assertEquals(LocalDate.of(2026, 8, 25), q?.period?.start)
    }

    @Test
    fun `yesterday resolves without a numeric date`() {
        val q = query("how many calories did I burn yesterday")
        assertEquals(Metric.CALORIES, q?.metric)
        assertEquals(today.minusDays(1), q?.period?.start)
    }

    // ------------------------------------------------------------ ranges

    @Test
    fun `week and month ranges resolve`() {
        val week = query("how many steps this week")
        assertEquals(Metric.STEPS, week?.metric)
        assertEquals(LocalDate.of(2026, 8, 24), week?.period?.start) // Monday
        assertTrue(week?.period?.isSingleDay == false)

        val month = query("total calories this month")
        assertEquals(Metric.CALORIES, month?.metric)
        assertEquals(LocalDate.of(2026, 8, 1), month?.period?.start)
    }

    @Test
    fun `average and count aggregates are recognised`() {
        assertEquals(Aggregate.AVERAGE, query("what is my average steps this week")?.aggregate)
        assertEquals(Aggregate.COUNT, query("how many times did I work out this month")?.aggregate)
    }

    // -------------------------------------------------------- catalogue

    @Test
    fun `catalogue questions are answered locally`() {
        assertEquals(Metric.FAVOURITES, metric("what are my favourites"))
        assertEquals(Metric.ROUTINES, metric("what are my routines"))
        assertEquals(Metric.RECORDS, metric("what are my records"))
    }

    // ------------------------------------------------------------- gates

    @Test
    fun `writes never route to tier 1 even when they mention stats`() {
        listOf(
            "set my step goal to 12000",
            "change my goal",
            "log a 30 minute run",
            "add push ups to my exercises",
            "update my weight to 80kg"
        ).forEach { assertNull(it, TierClassifier.classify(it, today)) }
    }

    /**
     * Regression: "how much protein should I eat after training" was answered
     * locally as workouts-today, because "training" read as a metric. A wrong
     * free answer is worse than a paid right one.
     */
    @Test
    fun `fitness words inside advice questions must not trigger a local answer`() {
        listOf(
            "how much protein should I eat after training",
            "how many calories should I eat to lose weight",
            "is soreness after lifting normal?",
            "should I stretch before running",
            "how do I improve my endurance",
            "what is the difference between reps and sets"
        ).forEach { assertNull(it, TierClassifier.classify(it, today)) }
    }

    @Test
    fun `a question with no personal reference goes to the model`() {
        assertNull(TierClassifier.classify("are steps a good measure of fitness", today))
        assertNull(TierClassifier.classify("what is progressive overload", today))
    }

    @Test
    fun `blank input goes to the model rather than guessing`() {
        assertNull(TierClassifier.classify("", today))
        assertNull(TierClassifier.classify("   ", today))
    }
}
