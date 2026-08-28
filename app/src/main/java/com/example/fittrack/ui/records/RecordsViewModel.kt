package com.example.fittrack.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.domain.model.DailySteps
import com.example.fittrack.domain.model.DayRecord
import com.example.fittrack.domain.model.ExerciseTally
import com.example.fittrack.domain.model.RecordsSummary
import com.example.fittrack.domain.model.WeekRecord
import com.example.fittrack.domain.model.WeekdayRecord
import com.example.fittrack.domain.model.Workout
import com.example.fittrack.domain.model.WorkoutRecord
import com.example.fittrack.domain.repository.StepRepository
import com.example.fittrack.domain.repository.WorkoutRepository
import com.example.fittrack.domain.util.CalorieCalculator
import com.example.fittrack.domain.util.StreakCalculator
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val stepRepository: StepRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<RecordsSummary>>(UiState.Loading)
    val uiState: StateFlow<UiState<RecordsSummary>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                workoutRepository.getAllWorkouts(),
                stepRepository.getAllSteps()
            ) { workouts, steps -> build(workouts, steps) }
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "Couldn't load records") }
                .collect { summary ->
                    _uiState.value =
                        if (summary == null) UiState.Empty else UiState.Success(summary)
                }
        }
    }

    /** Returns null when nothing has been recorded at all, so the screen can say so. */
    private fun build(workouts: List<Workout>, steps: List<DailySteps>): RecordsSummary? {
        val stepDays = steps.filter { it.stepCount > 0 }
        if (workouts.isEmpty() && stepDays.isEmpty()) return null

        val workoutsByDate: Map<LocalDate, List<Workout>> = workouts
            .mapNotNull { w -> w.date.toLocalDateOrNull()?.let { it to w } }
            .groupBy({ it.first }, { it.second })

        val stepsByDate: Map<LocalDate, Int> = stepDays
            .mapNotNull { s -> s.date.toLocalDateOrNull()?.let { it to s.stepCount } }
            .toMap()

        // A day counts as active if something was logged or the step counter moved.
        val activeDates = workoutsByDate.keys + stepsByDate.keys

        val bestStepDay = stepsByDate.maxByOrNull { it.value }
            ?.let { DayRecord(it.value, it.key) }

        // Daily calories mirror the Log screen: logged workouts plus walking.
        val caloriesByDate = activeDates.associateWith { date ->
            (workoutsByDate[date]?.sumOf { it.calories } ?: 0) +
                CalorieCalculator.caloriesFromSteps(stepsByDate[date] ?: 0)
        }
        val bestCalorieDay = caloriesByDate.filterValues { it > 0 }
            .maxByOrNull { it.value }
            ?.let { DayRecord(it.value, it.key) }

        val walkingCalories = stepsByDate.values.sumOf { CalorieCalculator.caloriesFromSteps(it) }

        val topExercises = workouts.groupBy { it.type }
            .map { (type, list) ->
                ExerciseTally(
                    name = type,
                    icon = list.firstNotNullOfOrNull { it.exerciseIcon },
                    count = list.size,
                    minutes = list.sumOf { it.durationMinutes },
                    calories = list.sumOf { it.calories }
                )
            }
            .sortedWith(
                compareByDescending<ExerciseTally> { it.count }.thenByDescending { it.minutes }
            )
            .take(TOP_EXERCISE_COUNT)

        val busiestWeekday = workoutsByDate.entries
            .groupBy { it.key.dayOfWeek }
            .map { (day, entries) -> day to entries.sumOf { it.value.size } }
            .maxByOrNull { it.second }
            ?.let { (day, count) ->
                WeekdayRecord(
                    dayName = day.name.lowercase().replaceFirstChar { it.uppercase() },
                    workouts = count
                )
            }

        return RecordsSummary(
            bestStepDay = bestStepDay,
            bestCalorieDay = bestCalorieDay,
            bestStepWeek = bestStepWeek(stepsByDate),
            longestWorkout = workouts.maxByOrNull { it.durationMinutes }?.toRecord(),
            hardestWorkout = workouts.maxByOrNull { it.calories }?.toRecord(),
            currentStreak = StreakCalculator.current(activeDates),
            longestStreak = StreakCalculator.longest(activeDates),
            activeDays = activeDates.size,
            totalCalories = workouts.sumOf { it.calories } + walkingCalories,
            totalActiveMinutes = workouts.sumOf { it.durationMinutes },
            totalWorkouts = workouts.size,
            totalSteps = stepsByDate.values.sum(),
            trainingSince = activeDates.minOrNull(),
            topExercises = topExercises,
            busiestWeekday = busiestWeekday
        )
    }

    /**
     * Best any-seven-consecutive-days window rather than the best calendar week,
     * so a strong stretch that straddles a Sunday still counts.
     */
    private fun bestStepWeek(stepsByDate: Map<LocalDate, Int>): WeekRecord? {
        if (stepsByDate.isEmpty()) return null
        return stepsByDate.keys.map { start ->
            val end = start.plusDays(6)
            val total = stepsByDate.entries
                .filter { it.key >= start && it.key <= end }
                .sumOf { it.value }
            WeekRecord(total, start, end)
        }.maxByOrNull { it.total }
    }

    private fun Workout.toRecord() = WorkoutRecord(
        name = type,
        icon = exerciseIcon,
        minutes = durationMinutes,
        calories = calories,
        date = date.toLocalDateOrNull() ?: LocalDate.now()
    )

    private fun String.toLocalDateOrNull(): LocalDate? =
        try {
            LocalDate.parse(this)
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val TOP_EXERCISE_COUNT = 3
    }
}
