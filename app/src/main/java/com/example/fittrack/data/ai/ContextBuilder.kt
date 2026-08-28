package com.example.fittrack.data.ai

import com.example.fittrack.data.prefs.GoalPreferences
import com.example.fittrack.data.prefs.UserPreferences
import com.example.fittrack.domain.repository.StepRepository
import com.example.fittrack.domain.repository.WorkoutRepository
import com.example.fittrack.domain.util.CalorieCalculator
import com.example.fittrack.domain.util.StreakCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/** A snapshot of the user's numbers, for both Tier 1 answers and model context. */
data class UserSnapshot(
    val goal: Int,
    val stepsToday: Int,
    val caloriesToday: Int,
    val walkingCaloriesToday: Int,
    val weightKg: Int,
    val streak: Int,
    val workoutsToday: List<Pair<String, Int>>,
    val weeklyAverageSteps: Int
) {
    val stepsRemaining: Int get() = (goal - stepsToday).coerceAtLeast(0)
    val goalPercent: Int get() = if (goal <= 0) 0 else (stepsToday * 100) / goal
}

/**
 * Turns the user's data into a compact line for the model.
 *
 * Deliberately a short string rather than a JSON dump: every token here is paid
 * for on every Tier 2 and Tier 3 call, and the model reads prose perfectly well.
 * Target is roughly 50 tokens.
 */
class ContextBuilder @Inject constructor(
    private val stepRepository: StepRepository,
    private val workoutRepository: WorkoutRepository,
    private val goalPreferences: GoalPreferences,
    private val userPreferences: UserPreferences
) {

    suspend fun snapshot(): UserSnapshot {
        val today = LocalDate.now()
        val todayKey = today.toString()

        val allSteps = stepRepository.getAllSteps().first()
        val allWorkouts = workoutRepository.getAllWorkouts().first()

        val stepsToday = allSteps.firstOrNull { it.date == todayKey }?.stepCount ?: 0
        val todayWorkouts = allWorkouts.filter { it.date == todayKey }
        val walkingCalories = CalorieCalculator.caloriesFromSteps(stepsToday)

        val activeDates = buildSet {
            allSteps.filter { it.stepCount > 0 }.forEach { day ->
                runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { add(it) }
            }
            allWorkouts.forEach { workout ->
                runCatching { LocalDate.parse(workout.date) }.getOrNull()?.let { add(it) }
            }
        }

        val lastSeven = (0..6).map { today.minusDays(it.toLong()).toString() }
        val weeklyAverage = lastSeven
            .map { key -> allSteps.firstOrNull { it.date == key }?.stepCount ?: 0 }
            .average()
            .toInt()

        return UserSnapshot(
            goal = goalPreferences.getGoal(),
            stepsToday = stepsToday,
            caloriesToday = todayWorkouts.sumOf { it.calories } + walkingCalories,
            walkingCaloriesToday = walkingCalories,
            weightKg = userPreferences.getWeightKg(),
            streak = StreakCalculator.current(activeDates, today),
            workoutsToday = todayWorkouts.map { it.type to it.durationMinutes },
            weeklyAverageSteps = weeklyAverage
        )
    }

    /** The compact line handed to the model. */
    fun summarise(snapshot: UserSnapshot): String = buildString {
        append("Goal ${snapshot.goal}. ")
        append("Today ${snapshot.stepsToday} (${snapshot.goalPercent}%). ")
        append("7d avg ${snapshot.weeklyAverageSteps}. ")
        append("Streak ${snapshot.streak}d. ")
        append("Weight ${snapshot.weightKg}kg. ")
        append("Burned today ${snapshot.caloriesToday} kcal. ")
        if (snapshot.workoutsToday.isEmpty()) {
            append("No workouts logged today.")
        } else {
            append("Today: ")
            append(snapshot.workoutsToday.joinToString(", ") { "${it.first} ${it.second}m" })
            append(".")
        }
    }
}
