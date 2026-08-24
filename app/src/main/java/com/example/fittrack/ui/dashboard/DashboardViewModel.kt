package com.example.fittrack.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.data.prefs.GoalPreferences
import com.example.fittrack.data.sensor.StepSensorManager
import com.example.fittrack.domain.model.DailySteps
import com.example.fittrack.domain.repository.StepRepository
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val stepSensorManager: StepSensorManager,
    private val stepRepository: StepRepository,
    private val goalPreferences: GoalPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<DailySteps>>(UiState.Loading)
    val uiState: StateFlow<UiState<DailySteps>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            stepSensorManager.observeSteps()
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "Sensor error") }
                .collect { rawValue ->
                    val daily = stepRepository.syncTodaySteps(rawValue)
                    _uiState.value = UiState.Success(daily.copy(goal = goalPreferences.getGoal()))
                }
        }
    }

    fun updateGoal(newGoal: Int) {
        goalPreferences.setGoal(newGoal)
        val current = _uiState.value
        if (current is UiState.Success) {
            _uiState.value = UiState.Success(current.data.copy(goal = newGoal))
        }
    }
}