package com.example.fittrack.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.data.local.StepDao
import com.example.fittrack.domain.model.DailySteps
import com.example.fittrack.domain.repository.StepRepository
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ChartsSummary(
    val days: List<DailySteps>,
    val weekLabel: String,
    val bestDaySteps: Int,
    val bestDayDate: String,
    val bestWeekTotal: Int,
    val bestMonthTotal: Int,
    val canGoForward: Boolean
)

@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val stepRepository: StepRepository,
    private val stepDao: StepDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ChartsSummary>>(UiState.Loading)
    val uiState: StateFlow<UiState<ChartsSummary>> = _uiState.asStateFlow()

    private var weekOffset = 0  // 0 = this week, -1 = last week, etc.

    init {
        viewModelScope.launch {
            stepRepository.seedMockDataIfNeeded()
            loadWeek()
        }
    }

    fun goToPreviousWeek() {
        weekOffset -= 1
        viewModelScope.launch { loadWeek() }
    }

    fun goToNextWeek() {
        if (weekOffset >= 0) return  // don't allow going into the future
        weekOffset += 1
        viewModelScope.launch { loadWeek() }
    }

    private suspend fun loadWeek() {
        _uiState.value = UiState.Loading

        val today = LocalDate.now()
        val weekStart = today.plusWeeks(weekOffset.toLong()).minusDays(today.dayOfWeek.value.toLong() - 1)
        val weekEnd = weekStart.plusDays(6)

        val weekEntities = stepDao.getStepsInRangeOnce(weekStart.toString(), weekEnd.toString())
        val days = weekEntities.map { DailySteps(it.date, it.stepCount) }
            .sortedBy { it.date }

        val monthStart = today.plusWeeks(weekOffset.toLong()).withDayOfMonth(1)
        val monthEnd = monthStart.plusMonths(1).minusDays(1)
        val monthEntities = stepDao.getStepsInRangeOnce(monthStart.toString(), monthEnd.toString())

        val best = weekEntities.maxByOrNull { it.stepCount }
        val weekTotal = weekEntities.sumOf { it.stepCount }
        val monthTotal = monthEntities.sumOf { it.stepCount }

        _uiState.value = UiState.Success(
            ChartsSummary(
                days = days,
                weekLabel = "${weekStart.month.name.take(3)} ${weekStart.dayOfMonth} - ${weekEnd.dayOfMonth}",
                bestDaySteps = best?.stepCount ?: 0,
                bestDayDate = best?.date ?: "—",
                bestWeekTotal = weekTotal,
                bestMonthTotal = monthTotal,
                canGoForward = weekOffset < 0
            )
        )
    }
}