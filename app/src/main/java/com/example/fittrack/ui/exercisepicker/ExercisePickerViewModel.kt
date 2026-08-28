package com.example.fittrack.ui.exercisepicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.data.prefs.UserPreferences
import com.example.fittrack.domain.model.Exercise
import com.example.fittrack.domain.model.ExerciseCategory
import com.example.fittrack.domain.model.ExerciseIntensity
import com.example.fittrack.domain.repository.ExerciseRepository
import com.example.fittrack.domain.util.CalorieCalculator
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One line of the picker list: either a section title or a tappable exercise. */
sealed class PickerRow {
    data class Header(val title: String) : PickerRow()
    data class Item(val exercise: Exercise, val kcalPerHour: Int) : PickerRow()
}

@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<PickerRow>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<PickerRow>>> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** null means the "All" tab. */
    private val _category = MutableStateFlow<ExerciseCategory?>(null)
    val category: StateFlow<ExerciseCategory?> = _category.asStateFlow()

    private val weightKg = userPreferences.getWeightKg()

    init {
        viewModelScope.launch {
            // First launch pays for the seed here; afterwards it is a single
            // key lookup, so the list still renders straight from Room.
            try {
                exerciseRepository.seedCatalogIfNeeded()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Couldn't load the exercise library")
                return@launch
            }

            combine(exerciseRepository.getAllExercises(), _query, _category) { all, query, category ->
                buildRows(all, query, category)
            }
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "Couldn't load exercises") }
                .collect { rows ->
                    _uiState.value = if (rows.isEmpty()) UiState.Empty else UiState.Success(rows)
                }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setCategory(value: ExerciseCategory?) {
        _category.value = value
    }

    fun toggleFavorite(exercise: Exercise) {
        viewModelScope.launch { exerciseRepository.setFavorite(exercise.id, !exercise.isFavorite) }
    }

    fun createCustomExercise(
        name: String,
        category: ExerciseCategory,
        icon: String,
        intensity: ExerciseIntensity
    ) {
        viewModelScope.launch {
            exerciseRepository.createCustomExercise(name.trim(), category, icon.trim(), intensity)
        }
    }

    fun updateCustomExercise(
        id: Long,
        name: String,
        category: ExerciseCategory,
        icon: String,
        intensity: ExerciseIntensity
    ) {
        viewModelScope.launch {
            exerciseRepository.updateCustomExercise(id, name.trim(), category, icon.trim(), intensity)
        }
    }

    fun deleteCustomExercise(exercise: Exercise) {
        viewModelScope.launch {
            exerciseRepository.deleteCustomExercise(exercise.id)
            // Otherwise the Log screen would reopen onto an exercise that is gone.
            if (userPreferences.getLastExerciseId() == exercise.id) {
                userPreferences.clearLastExerciseId()
            }
        }
    }

    /**
     * Favourites first, then whatever was used recently, then the rest split by
     * category. Search and the category tab filter the whole list before the
     * sections are cut, so a pinned favourite disappears if it does not match.
     */
    private fun buildRows(
        all: List<Exercise>,
        query: String,
        category: ExerciseCategory?
    ): List<PickerRow> {
        val trimmed = query.trim()
        val filtered = all.filter { exercise ->
            (category == null || exercise.category == category) &&
                (trimmed.isEmpty() || exercise.name.contains(trimmed, ignoreCase = true))
        }
        if (filtered.isEmpty()) return emptyList()

        val favorites = filtered.filter { it.isFavorite }.sortedBy { it.name.lowercase() }
        val favoriteIds = favorites.map { it.id }.toSet()

        val recent = filtered
            .filter { it.id !in favoriteIds && it.lastUsedAt != null }
            .sortedByDescending { it.lastUsedAt }
            .take(RECENT_LIMIT)
        val recentIds = recent.map { it.id }.toSet()

        val rest = filtered.filter { it.id !in favoriteIds && it.id !in recentIds }

        val rows = mutableListOf<PickerRow>()
        if (favorites.isNotEmpty()) {
            rows += PickerRow.Header("Favourites")
            rows += favorites.map { it.toRow() }
        }
        if (recent.isNotEmpty()) {
            rows += PickerRow.Header("Recently used")
            rows += recent.map { it.toRow() }
        }
        if (rest.isNotEmpty()) {
            val custom = rest.filter { it.isCustom }.sortedBy { it.name.lowercase() }
            if (custom.isNotEmpty()) {
                rows += PickerRow.Header("Your exercises")
                rows += custom.map { it.toRow() }
            }
            val builtIn = rest.filter { !it.isCustom }
            if (category != null) {
                if (builtIn.isNotEmpty()) {
                    rows += PickerRow.Header(category.displayName)
                    rows += builtIn.sortedBy { it.name.lowercase() }.map { it.toRow() }
                }
            } else {
                ExerciseCategory.entries.forEach { group ->
                    val inGroup = builtIn.filter { it.category == group }
                    if (inGroup.isNotEmpty()) {
                        rows += PickerRow.Header(group.displayName)
                        rows += inGroup.sortedBy { it.name.lowercase() }.map { it.toRow() }
                    }
                }
            }
        }
        return rows
    }

    private fun Exercise.toRow() = PickerRow.Item(this, CalorieCalculator.kcalPerHour(met, weightKg))

    private companion object {
        const val RECENT_LIMIT = 6
    }
}
