package com.example.fittrack.ui.logworkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.domain.model.Workout
import com.example.fittrack.domain.repository.StepRepository
import com.example.fittrack.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class LogWorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val stepRepository: StepRepository
) : ViewModel() {

    private val today = LocalDate.now().toString()

    private val _todayWorkouts = MutableStateFlow<List<Workout>>(emptyList())
    val todayWorkouts: StateFlow<List<Workout>> = _todayWorkouts.asStateFlow()

    private val _todaySteps = MutableStateFlow(0)
    val todaySteps: StateFlow<Int> = _todaySteps.asStateFlow()

    init {
        viewModelScope.launch {
            workoutRepository.getAllWorkouts()
                .map { all -> all.filter { it.date == today } }
                .collect { _todayWorkouts.value = it }
        }
        viewModelScope.launch {
            stepRepository.getStepsForDate(today)
                .collect { _todaySteps.value = it?.stepCount ?: 0 }
        }
    }

    fun estimateCalories(type: String, minutes: Int): Int {
        val perMinute = when (type) {
            "Running" -> 11
            "Cycling" -> 8
            "Gym" -> 6
            else -> 6
        }
        return perMinute * minutes
    }

    fun caloriesFromSteps(steps: Int): Int = (steps * 0.04).toInt()

    fun submitActivity(type: String, minutes: Int, notes: String?) {
        if (minutes <= 0) return
        viewModelScope.launch {
            workoutRepository.addWorkout(
                Workout(
                    type = type,
                    durationMinutes = minutes,
                    calories = estimateCalories(type, minutes),
                    date = today,
                    notes = notes?.takeIf { it.isNotBlank() }
                )
            )
        }
    }
}