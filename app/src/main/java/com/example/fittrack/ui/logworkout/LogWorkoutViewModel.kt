package com.example.fittrack.ui.logworkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.domain.model.Workout
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
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val today = LocalDate.now().toString()

    val todayWorkouts: StateFlow<List<Workout>> = MutableStateFlow<List<Workout>>(emptyList()).also { flow ->
        viewModelScope.launch {
            workoutRepository.getAllWorkouts()
                .map { all -> all.filter { it.date == today } }
                .collect { flow.value = it }
        }
    }.asStateFlow()

    fun estimateCalories(type: String, minutes: Int): Int {
        val perMinute = when (type) {
            "Running" -> 11
            "Cycling" -> 8
            "Gym" -> 6
            "Walking" -> 4
            else -> 6
        }
        return perMinute * minutes
    }

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