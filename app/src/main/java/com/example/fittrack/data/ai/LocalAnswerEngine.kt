package com.example.fittrack.data.ai

import com.example.fittrack.data.prefs.GoalPreferences
import com.example.fittrack.data.prefs.UserPreferences
import com.example.fittrack.domain.model.Workout
import com.example.fittrack.domain.repository.ExerciseRepository
import com.example.fittrack.domain.repository.RoutineRepository
import com.example.fittrack.domain.repository.StepRepository
import com.example.fittrack.domain.repository.WorkoutRepository
import com.example.fittrack.domain.util.CalorieCalculator
import com.example.fittrack.domain.util.StreakCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * Answers a [LocalQuery] straight from the repositories -- no network, no cost,
 * works offline.
 *
 * Every reply names the period it measured ("on Tue 25 Aug", "this week") so an
 * answer can never be silently about the wrong days. When a past date has no
 * data at all it says so plainly rather than reporting zeroes as if they were
 * a real result.
 */
class LocalAnswerEngine @Inject constructor(
    private val stepRepository: StepRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
    private val goalPreferences: GoalPreferences,
    private val userPreferences: UserPreferences
) {

    suspend fun answer(query: LocalQuery, today: LocalDate = LocalDate.now()): String {
        val period = query.period

        return when (query.metric) {
            Metric.HELP -> help()
            Metric.GOAL -> goal(today)
            Metric.WEIGHT -> "Your body weight is set to ${userPreferences.getWeightKg()} kg. " +
                "Calorie estimates use it."
            Metric.STREAK -> streak(today)
            Metric.FAVOURITES -> favourites()
            Metric.CUSTOM_EXERCISES -> customExercises()
            Metric.ROUTINES -> routines()
            Metric.RECORDS -> records(today)
            Metric.STEPS -> steps(query, today)
            Metric.CALORIES -> calories(query)
            Metric.WORKOUTS -> workouts(query)
            Metric.ACTIVE_MINUTES -> activeMinutes(query)
            Metric.SUMMARY -> summary(period)
        }
    }

    // ------------------------------------------------------------- metrics

    private suspend fun steps(query: LocalQuery, today: LocalDate): String {
        val period = query.period
        val days = stepsIn(period)
        val total = days.values.sum()
        val goal = goalPreferences.getGoal()

        if (period.isSingleDay) {
            val date = period.start
            val count = days[date]
            if (count == null && date != today) return noData(period)
            val steps = count ?: 0

            if (query.aggregate == Aggregate.REMAINING || date == today) {
                val remaining = (goal - steps).coerceAtLeast(0)
                val percent = if (goal <= 0) 0 else steps * 100 / goal
                return if (date == today) {
                    if (remaining == 0) {
                        "You've hit your goal of $goal steps today, on $steps so far."
                    } else {
                        "$remaining steps to go ${period.label}. You're on $steps of $goal ($percent%)."
                    }
                } else {
                    "$steps steps ${period.label}, $percent% of your $goal goal."
                }
            }
            return "$steps steps ${period.label}."
        }

        if (days.isEmpty()) return noData(period)

        return when (query.aggregate) {
            Aggregate.AVERAGE ->
                "You averaged ${total / days.size} steps a day over ${period.label}, " +
                    "across ${days.size} tracked ${dayWord(days.size)}."
            Aggregate.BEST -> {
                val best = days.maxByOrNull { it.value }!!
                "Your best day ${period.label} was ${best.value} steps, " +
                    "${DateExpressionParser.parse(best.key.toString(), today)?.label ?: best.key}."
            }
            else ->
                "$total steps ${period.label}, averaging ${total / days.size} a day " +
                    "over ${days.size} tracked ${dayWord(days.size)}."
        }
    }

    private suspend fun calories(query: LocalQuery): String {
        val period = query.period
        val logged = workoutsIn(period)
        val stepDays = stepsIn(period)
        val walking = stepDays.values.sumOf { CalorieCalculator.caloriesFromSteps(it) }
        val fromWorkouts = logged.sumOf { it.calories }
        val total = fromWorkouts + walking

        if (logged.isEmpty() && stepDays.isEmpty()) return noData(period)

        return if (period.isSingleDay) {
            "About $total kcal ${period.label}: $fromWorkouts from workouts and $walking from walking."
        } else {
            "About $total kcal ${period.label}, $fromWorkouts of it from " +
                "${logged.size} logged ${workoutWord(logged.size)}."
        }
    }

    private suspend fun workouts(query: LocalQuery): String {
        val period = query.period
        val logged = workoutsIn(period)

        if (logged.isEmpty()) {
            return if (period.isSingleDay) {
                "Nothing logged ${period.label}."
            } else {
                "No workouts logged ${period.label}."
            }
        }

        if (query.aggregate == Aggregate.COUNT) {
            val byType = logged.groupBy { it.type }
                .entries.sortedByDescending { it.value.size }
                .joinToString(", ") { "${it.key} x${it.value.size}" }
            return "${logged.size} ${workoutWord(logged.size)} ${period.label}: $byType."
        }

        val detail = logged.joinToString(", ") {
            "${it.type} ${it.durationMinutes} min"
        }
        val kcal = logged.sumOf { it.calories }
        return "${period.label.replaceFirstChar { it.uppercase() }}: $detail. " +
            "That's ${logged.sumOf { it.durationMinutes }} minutes and about $kcal kcal."
    }

    private suspend fun activeMinutes(query: LocalQuery): String {
        val logged = workoutsIn(query.period)
        if (logged.isEmpty()) return noData(query.period)
        val minutes = logged.sumOf { it.durationMinutes }
        val pretty = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"
        return "$pretty of logged activity ${query.period.label}, " +
            "across ${logged.size} ${workoutWord(logged.size)}."
    }

    private suspend fun summary(period: DatePeriod): String {
        val logged = workoutsIn(period)
        val stepDays = stepsIn(period)
        val steps = stepDays.values.sum()

        if (logged.isEmpty() && steps == 0) return noData(period)

        val parts = mutableListOf<String>()
        if (logged.isNotEmpty()) {
            parts += logged.joinToString(", ") { "${it.type} ${it.durationMinutes} min" }
        }
        if (steps > 0) parts += "$steps steps"

        val kcal = logged.sumOf { it.calories } +
            stepDays.values.sumOf { CalorieCalculator.caloriesFromSteps(it) }

        return "${period.label.replaceFirstChar { it.uppercase() }}: " +
            "${parts.joinToString(" and ")}. About $kcal kcal in total."
    }

    // --------------------------------------------------------- catalogue

    private fun goal(today: LocalDate): String {
        val goal = goalPreferences.getGoal()
        return "Your daily goal is $goal steps."
    }

    private suspend fun streak(today: LocalDate): String {
        val active = activeDates()
        val current = StreakCalculator.current(active, today)
        val longest = StreakCalculator.longest(active)
        return if (current == 0) {
            "No active streak right now. Your longest was $longest ${dayWord(longest)}."
        } else {
            "You're on a $current-day streak. Your longest is $longest ${dayWord(longest)}."
        }
    }

    private suspend fun favourites(): String {
        val favourites = exerciseRepository.getAllExercises().first().filter { it.isFavorite }
        return if (favourites.isEmpty()) {
            "You haven't starred any exercises yet. Tap the star in the exercise library."
        } else {
            "Your favourites: ${favourites.joinToString(", ") { it.name }}."
        }
    }

    private suspend fun customExercises(): String {
        val custom = exerciseRepository.getAllExercises().first().filter { it.isCustom }
        return if (custom.isEmpty()) {
            "You haven't created any custom exercises yet."
        } else {
            "Your own exercises: ${custom.joinToString(", ") { "${it.name} (${it.met} MET)" }}."
        }
    }

    private suspend fun routines(): String {
        val routines = routineRepository.getAllRoutines().first()
        return if (routines.isEmpty()) {
            "You haven't built any gym sessions yet."
        } else {
            "Your sessions: " + routines.joinToString(", ") {
                "${it.name} (${it.exerciseCount} exercises, ${it.totalMinutes} min)"
            } + "."
        }
    }

    private suspend fun records(today: LocalDate): String {
        val stepDays = allSteps()
        val logged = workoutRepository.getAllWorkouts().first()
        val bestStepDay = stepDays.maxByOrNull { it.value }
        val longest = logged.maxByOrNull { it.durationMinutes }
        val hardest = logged.maxByOrNull { it.calories }

        val parts = mutableListOf<String>()
        bestStepDay?.let { parts += "best step day ${it.value} steps" }
        longest?.let { parts += "longest workout ${it.type} ${it.durationMinutes} min" }
        hardest?.let { parts += "hardest ${it.type} ${it.calories} kcal" }
        parts += "longest streak ${StreakCalculator.longest(activeDates(stepDays, logged))} days"

        return if (parts.isEmpty()) {
            "No records yet. Log something and they'll start showing up."
        } else {
            "Your records: ${parts.joinToString(", ")}."
        }
    }

    private fun help(): String =
        "Ask me about your steps, calories, workouts, streak, goal or weight, for today " +
            "or any past date (\"what did I do on 25-8\", \"steps last week\"). I can also " +
            "set your goal or weight, log a workout, add a custom exercise, or answer " +
            "general fitness questions."

    // ------------------------------------------------------------ helpers

    private suspend fun allSteps(): Map<LocalDate, Int> =
        stepRepository.getAllSteps().first()
            .mapNotNull { day -> day.date.toDate()?.let { it to day.stepCount } }
            .toMap()

    private suspend fun stepsIn(period: DatePeriod): Map<LocalDate, Int> =
        allSteps().filterKeys { period.contains(it) }.filterValues { it > 0 }

    private suspend fun workoutsIn(period: DatePeriod): List<Workout> =
        workoutRepository.getAllWorkouts().first()
            .filter { workout -> workout.date.toDate()?.let { period.contains(it) } == true }

    private suspend fun activeDates(): Set<LocalDate> =
        activeDates(allSteps(), workoutRepository.getAllWorkouts().first())

    private fun activeDates(steps: Map<LocalDate, Int>, workouts: List<Workout>): Set<LocalDate> =
        buildSet {
            steps.filterValues { it > 0 }.keys.forEach { add(it) }
            workouts.forEach { workout -> workout.date.toDate()?.let { add(it) } }
        }

    /** Never report zeroes for a day that simply was not tracked. */
    private fun noData(period: DatePeriod): String =
        if (period.isSingleDay) {
            "I have nothing recorded ${period.label}."
        } else {
            "I have nothing recorded for ${period.label}."
        }

    private fun dayWord(n: Int) = if (n == 1) "day" else "days"
    private fun workoutWord(n: Int) = if (n == 1) "workout" else "workouts"

    private fun String.toDate(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
}
