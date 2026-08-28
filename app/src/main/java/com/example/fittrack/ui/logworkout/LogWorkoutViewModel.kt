package com.example.fittrack.ui.logworkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.data.prefs.UserPreferences
import com.example.fittrack.domain.model.Exercise
import com.example.fittrack.domain.model.Routine
import com.example.fittrack.domain.model.Workout
import com.example.fittrack.domain.repository.ExerciseRepository
import com.example.fittrack.domain.repository.RoutineRepository
import com.example.fittrack.domain.repository.StepRepository
import com.example.fittrack.domain.repository.WorkoutRepository
import com.example.fittrack.domain.util.CalorieCalculator
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

/** Everything the Log screen draws, in one snapshot. */
data class LogScreenState(
    val selectedExercise: Exercise?,
    val durationMinutes: Int,
    val estimatedCalories: Int,
    /** How many times the selected exercise has already been logged today. */
    val selectedLoggedToday: Int,
    val weightKg: Int,
    val todaySteps: Int,
    val walkingCalories: Int,
    val todayWorkouts: List<Workout>,
    /** Saved gym sessions, most recently logged first. */
    val routines: List<Routine>
) {
    val loggedCalories: Int get() = todayWorkouts.sumOf { it.calories }
    val totalCalories: Int get() = loggedCalories + walkingCalories

    fun caloriesForRoutine(routine: Routine): Int = routine.items.sumOf {
        CalorieCalculator.caloriesBurned(it.exercise.met, weightKg, it.durationMinutes)
    }

    fun isRoutineLoggedToday(routine: Routine): Boolean =
        todayWorkouts.any { it.sessionName == routine.name }
}

@HiltViewModel
class LogWorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val stepRepository: StepRepository,
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val today = LocalDate.now().toString()

    private val _uiState = MutableStateFlow<UiState<LogScreenState>>(UiState.Loading)
    val uiState: StateFlow<UiState<LogScreenState>> = _uiState.asStateFlow()

    private val selectedExercise = MutableStateFlow<Exercise?>(null)
    private val duration = MutableStateFlow(DEFAULT_DURATION_MINUTES)
    private val weightKg = MutableStateFlow(userPreferences.getWeightKg())

    init {
        // Reopen on whatever was logged last, so the common case is one tap.
        viewModelScope.launch {
            userPreferences.getLastExerciseId()?.let { id ->
                val exercise = exerciseRepository.getExercise(id)
                if (exercise == null) userPreferences.clearLastExerciseId()
                selectedExercise.value = exercise
            }
        }

        viewModelScope.launch {
            val workouts = workoutRepository.getAllWorkouts()
            val steps = stepRepository.getStepsForDate(today)

            // combine() is only typed up to five flows, so the core state is
            // built first and the sessions folded in by a second combine.
            val core = combine(workouts, steps, selectedExercise, duration, weightKg) {
                    all, dailySteps, exercise, minutes, weight ->
                val todayWorkouts = all.filter { it.date == today }
                val stepCount = dailySteps?.stepCount ?: 0
                LogScreenState(
                    selectedExercise = exercise,
                    durationMinutes = minutes,
                    estimatedCalories = exercise?.let {
                        CalorieCalculator.caloriesBurned(it.met, weight, minutes)
                    } ?: 0,
                    selectedLoggedToday = exercise?.let { picked ->
                        todayWorkouts.count { it.type == picked.name }
                    } ?: 0,
                    weightKg = weight,
                    todaySteps = stepCount,
                    walkingCalories = caloriesFromSteps(stepCount),
                    todayWorkouts = todayWorkouts,
                    routines = emptyList()
                )
            }

            combine(core, routineRepository.getAllRoutines()) { state, routines ->
                state.copy(routines = routines)
            }
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "Couldn't load today") }
                .collect { state ->
                    _uiState.value = if (
                        state.selectedExercise == null &&
                        state.todayWorkouts.isEmpty() &&
                        state.todaySteps == 0 &&
                        state.routines.isEmpty()
                    ) {
                        UiState.Empty
                    } else {
                        UiState.Success(state)
                    }
                }
        }
    }

    fun selectExercise(id: Long) {
        viewModelScope.launch {
            val exercise = exerciseRepository.getExercise(id) ?: return@launch
            selectedExercise.value = exercise
            duration.value = DEFAULT_DURATION_MINUTES
            userPreferences.setLastExerciseId(id)
        }
    }

    /**
     * Re-reads the chosen exercise after a trip to the library, which is where
     * it could have been renamed, re-graded or deleted.
     */
    fun refreshSelection() {
        val current = selectedExercise.value ?: return
        viewModelScope.launch {
            val updated = exerciseRepository.getExercise(current.id)
            if (updated == null) userPreferences.clearLastExerciseId()
            selectedExercise.value = updated
        }
    }

    fun changeDuration(deltaMinutes: Int) {
        duration.value = (duration.value + deltaMinutes)
            .coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
    }

    fun updateWeight(newWeightKg: Int) {
        userPreferences.setWeightKg(newWeightKg)
        weightKg.value = userPreferences.getWeightKg()
    }

    /** Walking stays on the per-step estimate rather than the MET formula. */
    fun caloriesFromSteps(steps: Int): Int = CalorieCalculator.caloriesFromSteps(steps)

    fun submitActivity(notes: String?) {
        val exercise = selectedExercise.value ?: return
        val minutes = duration.value
        if (minutes <= 0) return
        viewModelScope.launch {
            workoutRepository.addWorkout(
                Workout(
                    type = exercise.name,
                    durationMinutes = minutes,
                    calories = CalorieCalculator.caloriesBurned(exercise.met, weightKg.value, minutes),
                    date = today,
                    notes = notes?.takeIf { it.isNotBlank() },
                    exerciseIcon = exercise.icon
                )
            )
            exerciseRepository.markUsed(exercise.id)
        }
    }

    /**
     * Replays a saved session: one workout row per exercise, all stamped with
     * the session name so History can show they belong together.
     */
    fun logRoutine(routine: Routine, notes: String?) {
        if (routine.items.isEmpty()) return
        val weight = weightKg.value
        viewModelScope.launch {
            workoutRepository.addWorkouts(
                routine.items.map { item ->
                    Workout(
                        type = item.exercise.name,
                        durationMinutes = item.durationMinutes,
                        calories = CalorieCalculator.caloriesBurned(
                            item.exercise.met, weight, item.durationMinutes
                        ),
                        date = today,
                        notes = notes?.takeIf { it.isNotBlank() },
                        exerciseIcon = item.exercise.icon,
                        sessionName = routine.name
                    )
                }
            )
            routine.items.forEach { exerciseRepository.markUsed(it.exercise.id) }
            routineRepository.markUsed(routine.id)
        }
    }

    companion object {
        const val DEFAULT_DURATION_MINUTES = 30
        const val DURATION_STEP_MINUTES = 5
        private const val MIN_DURATION_MINUTES = 5
        private const val MAX_DURATION_MINUTES = 600
    }
}
