package com.example.fittrack.domain.util

import com.example.fittrack.domain.model.CommunityMetric
import com.example.fittrack.domain.model.DailySteps
import com.example.fittrack.domain.model.Workout
import java.time.LocalDate

/**
 * Turns this week's local activity into the single number a leaderboard ranks
 * on.
 *
 * Pure and free of Android and Firestore on purpose: this is the one piece of
 * the community feature whose correctness is worth testing directly, since a
 * wrong number here is publicly visible to everyone the user is competing with.
 */
object WeeklyScoreCalculator {

    fun scoreFor(
        metric: CommunityMetric,
        steps: List<DailySteps>,
        workouts: List<Workout>,
        today: LocalDate = LocalDate.now()
    ): Int {
        // A set rather than a range check, because both sources store dates as
        // strings and a malformed one must simply not count rather than throw.
        val week = CommunityWeek.daysSoFar(today).toSet()

        val weekSteps = steps.filter { it.date.toDateOrNull() in week }
        val weekWorkouts = workouts.filter { it.date.toDateOrNull() in week }

        return when (metric) {
            CommunityMetric.STEPS -> weekSteps.sumOf { it.stepCount }
            CommunityMetric.ACTIVE_MINUTES -> weekWorkouts.sumOf { it.durationMinutes }
            CommunityMetric.WORKOUTS -> weekWorkouts.size
            // Steps are counted alongside logged sessions, because a member
            // whose week was all walking has genuinely burned those calories
            // and would otherwise show as zero.
            CommunityMetric.CALORIES ->
                weekWorkouts.sumOf { it.calories } +
                    CalorieCalculator.caloriesFromSteps(weekSteps.sumOf { it.stepCount })
        }
    }

    private fun String.toDateOrNull(): LocalDate? =
        runCatching { LocalDate.parse(this) }.getOrNull()
}
