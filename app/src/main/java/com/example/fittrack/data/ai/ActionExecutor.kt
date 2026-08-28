package com.example.fittrack.data.ai

import com.example.fittrack.data.prefs.GoalPreferences
import com.example.fittrack.data.prefs.UserPreferences
import com.example.fittrack.domain.model.AiAction
import com.example.fittrack.domain.model.Exercise
import com.example.fittrack.domain.model.ExerciseCategory
import com.example.fittrack.domain.model.ExerciseIntensity
import com.example.fittrack.domain.model.Workout
import com.example.fittrack.domain.repository.ExerciseRepository
import com.example.fittrack.domain.repository.WorkoutRepository
import com.example.fittrack.domain.util.CalorieCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.abs

sealed class ActionResult {
    data class Done(val message: String) : ActionResult()
    /** Validation refused it. [message] is already user-readable. */
    data class Rejected(val message: String) : ActionResult()
}

/**
 * Validates and runs an [AiAction].
 *
 * Everything the model produces is treated as untrusted input: ranges are
 * checked, exercise names are resolved against the real catalogue, and anything
 * that does not pass is refused with a reason rather than clamped silently. A
 * silently clamped value would look to the user like the assistant obeyed.
 */
class ActionExecutor @Inject constructor(
    private val goalPreferences: GoalPreferences,
    private val userPreferences: UserPreferences,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository
) {

    suspend fun execute(action: AiAction): ActionResult = when (action) {
        is AiAction.SetStepGoal -> setStepGoal(action)
        is AiAction.SetUserWeight -> setWeight(action)
        is AiAction.LogWorkout -> logWorkout(action)
        is AiAction.AddCustomExercise -> addExercise(action)
        is AiAction.QueryStats, AiAction.Answer -> ActionResult.Done("")
    }

    /** Human-readable description of a pending write, for the confirm chip. */
    suspend fun describe(action: AiAction): String = when (action) {
        is AiAction.SetStepGoal -> "Set your daily step goal to ${action.steps}?"
        is AiAction.SetUserWeight -> "Set your body weight to ${action.kg} kg?"
        is AiAction.LogWorkout -> {
            val match = resolveExercise(action.exerciseName)
            val name = match?.name ?: action.exerciseName
            "Log $name for ${action.minutes} minutes today?"
        }
        is AiAction.AddCustomExercise ->
            "Add \"${action.name}\" to your exercises as ${action.category}, ${action.intensity}?"
        else -> ""
    }

    private fun setStepGoal(action: AiAction.SetStepGoal): ActionResult {
        if (action.steps !in MIN_GOAL..MAX_GOAL) {
            return ActionResult.Rejected(
                "A daily step goal of ${action.steps} looks wrong. " +
                    "Pick something between $MIN_GOAL and $MAX_GOAL."
            )
        }
        goalPreferences.setGoal(action.steps)
        return ActionResult.Done("Daily step goal is now ${action.steps}.")
    }

    private fun setWeight(action: AiAction.SetUserWeight): ActionResult {
        if (action.kg !in MIN_WEIGHT..MAX_WEIGHT) {
            return ActionResult.Rejected(
                "A body weight of ${action.kg} kg looks wrong. " +
                    "Pick something between $MIN_WEIGHT and $MAX_WEIGHT kg."
            )
        }
        userPreferences.setWeightKg(action.kg)
        return ActionResult.Done("Body weight is now ${action.kg} kg.")
    }

    private suspend fun logWorkout(action: AiAction.LogWorkout): ActionResult {
        if (action.minutes !in MIN_MINUTES..MAX_MINUTES) {
            return ActionResult.Rejected(
                "A duration of ${action.minutes} minutes looks wrong. " +
                    "Pick something between $MIN_MINUTES and $MAX_MINUTES."
            )
        }
        val exercise = resolveExercise(action.exerciseName)
            ?: return ActionResult.Rejected(
                "I couldn't find \"${action.exerciseName}\" in your exercise library. " +
                    "Try the exact name, or add it as a custom exercise first."
            )

        val weight = userPreferences.getWeightKg()
        val calories = CalorieCalculator.caloriesBurned(exercise.met, weight, action.minutes)

        workoutRepository.addWorkout(
            Workout(
                type = exercise.name,
                durationMinutes = action.minutes,
                calories = calories,
                date = LocalDate.now().toString(),
                notes = null,
                exerciseIcon = exercise.icon
            )
        )
        exerciseRepository.markUsed(exercise.id)
        return ActionResult.Done(
            "Logged ${exercise.name} for ${action.minutes} min, about $calories kcal."
        )
    }

    private suspend fun addExercise(action: AiAction.AddCustomExercise): ActionResult {
        val name = action.name.trim()
        if (name.length < 2) {
            return ActionResult.Rejected("That exercise name is too short.")
        }
        val category = ExerciseCategory.fromStorage(action.category)
            ?: return ActionResult.Rejected(
                "\"${action.category}\" is not one of the categories. " +
                    "Use Cardio, Strength, Flexibility or Sports."
            )
        val intensity = ExerciseIntensity.fromStorage(action.intensity)
            ?: return ActionResult.Rejected(
                "\"${action.intensity}\" is not an intensity. Use Light, Moderate or Intense."
            )

        val existing = exerciseRepository.getAllExercises().first()
        if (existing.any { it.name.equals(name, ignoreCase = true) }) {
            return ActionResult.Rejected("\"$name\" is already in your exercise library.")
        }

        exerciseRepository.createCustomExercise(
            name = name,
            category = category,
            icon = action.icon?.takeIf { it.isNotBlank() } ?: DEFAULT_ICON,
            intensity = intensity
        )
        return ActionResult.Done(
            "Added $name to your exercises (${category.displayName}, ${intensity.displayName})."
        )
    }

    /**
     * Exact match first, then prefix/substring, then a light edit-distance pass
     * so "runing" and "push ups" still land. Anything vaguer is refused rather
     * than guessed at, because logging the wrong exercise is silent noise.
     */
    suspend fun resolveExercise(query: String): Exercise? {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return null
        val all = exerciseRepository.getAllExercises().first()
        if (all.isEmpty()) return null

        all.firstOrNull { it.name.lowercase() == needle }?.let { return it }

        val normalisedNeedle = normalise(needle)
        all.firstOrNull { normalise(it.name) == normalisedNeedle }?.let { return it }

        // Prefix beats substring: for "run" that picks a Running entry rather
        // than Trail Running, which merely contains the word.
        val startsWith = all.filter { normalise(it.name).startsWith(normalisedNeedle) }
        if (startsWith.isNotEmpty()) return startsWith.minByOrNull { it.name.length }

        val contains = all.filter { normalise(it.name).contains(normalisedNeedle) }
        if (contains.isNotEmpty()) return contains.minByOrNull { it.name.length }

        return all
            .map { it to distance(normalise(it.name), normalisedNeedle) }
            .filter { (exercise, dist) -> dist <= maxDistanceFor(exercise.name) }
            .minByOrNull { it.second }
            ?.first
    }

    private fun normalise(value: String) =
        value.lowercase().filter { it.isLetterOrDigit() }

    private fun maxDistanceFor(name: String) = when {
        name.length <= 5 -> 1
        name.length <= 10 -> 2
        else -> 3
    }

    /** Levenshtein, two-row variant; the catalogue is ~100 rows so this is cheap. */
    private fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        if (abs(a.length - b.length) > 6) return Int.MAX_VALUE / 2

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    companion object {
        const val MIN_GOAL = 1_000
        const val MAX_GOAL = 50_000
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 300
        const val MIN_WEIGHT = 30
        const val MAX_WEIGHT = 250
        private const val DEFAULT_ICON = "💪"
    }
}
