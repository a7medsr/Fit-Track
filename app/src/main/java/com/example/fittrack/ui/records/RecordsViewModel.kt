package com.example.fittrack.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.domain.model.StepRecords
import com.example.fittrack.domain.repository.StepRepository
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val stepRepository: StepRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<StepRecords>>(UiState.Loading)
    val uiState: StateFlow<UiState<StepRecords>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val records = stepRepository.getRecords()
                _uiState.value = UiState.Success(records)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load records")
            }
        }
    }
}