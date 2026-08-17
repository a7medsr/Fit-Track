package com.example.fittrack.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.domain.model.DailySteps
import com.example.fittrack.domain.repository.StepRepository
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val stepRepository: StepRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<DailySteps>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<DailySteps>>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            stepRepository.seedMockDataIfNeeded()

            val today = LocalDate.now()
            val weekAgo = today.minusDays(6)

            stepRepository.getStepsInRange(weekAgo.toString(), today.toString())
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "Failed to load") }
                .collect { steps -> _uiState.value = UiState.Success(steps) }
        }
    }
}