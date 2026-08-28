package com.example.fittrack.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.domain.model.Workout
import com.example.fittrack.domain.repository.WorkoutRepository
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DaySummary(
    val date: String,
    val dayName: String,
    val totalCalories: Int,
    val workouts: List<Workout>
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<DaySummary>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<DaySummary>>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            workoutRepository.getAllWorkouts()
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "Failed to load") }
                .collect { workouts ->
                    val grouped = workouts
                        .groupBy { it.date }
                        .map { (date, list) ->
                            DaySummary(
                                date = date,
                                dayName = dayNameFor(date),
                                totalCalories = list.sumOf { it.calories },
                                workouts = list
                            )
                        }
                        .sortedByDescending { it.date }
                    _uiState.value =
                        if (grouped.isEmpty()) UiState.Empty else UiState.Success(grouped)
                }
        }
    }

    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch { workoutRepository.deleteWorkout(workout) }
    }

    private fun dayNameFor(dateStr: String): String {
        return try {
            val date = java.time.LocalDate.parse(dateStr)
            val today = java.time.LocalDate.now()
            when (date) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            }
        } catch (e: Exception) {
            dateStr
        }
    }
}