package com.example.fittrack.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.data.prefs.UserPreferences
import com.example.fittrack.domain.model.RoutineItem
import com.example.fittrack.domain.repository.ExerciseRepository
import com.example.fittrack.domain.repository.RoutineRepository
import com.example.fittrack.domain.util.CalorieCalculator
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The session being built, priced against the user's current body weight. */
data class RoutineDraft(
    val name: String,
    val icon: String,
    val items: List<RoutineItem>,
    val weightKg: Int,
    val isEditing: Boolean
) {
    val totalMinutes: Int get() = items.sumOf { it.durationMinutes }

    val totalCalories: Int
        get() = items.sumOf {
            CalorieCalculator.caloriesBurned(it.exercise.met, weightKg, it.durationMinutes)
        }

    fun caloriesFor(item: RoutineItem): Int =
        CalorieCalculator.caloriesBurned(item.exercise.met, weightKg, item.durationMinutes)
}

/** One-shot signals the editor sends to its Activity. */
sealed class RoutineEditorEvent {
    /** Written to the database; the screen can close. */
    object Saved : RoutineEditorEvent()
    /** An existing session was loaded, so the name and emoji fields can be filled in. */
    data class Loaded(val name: String, val icon: String) : RoutineEditorEvent()
}

@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository,
    userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<RoutineDraft>>(UiState.Loading)
    val uiState: StateFlow<UiState<RoutineDraft>> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RoutineEditorEvent>()
    val events: SharedFlow<RoutineEditorEvent> = _events.asSharedFlow()

    private val weightKg = userPreferences.getWeightKg()

    private var routineId: Long = 0
    private var loaded = false
    private var name: String = ""
    private var icon: String = DEFAULT_ICON
    private val items = mutableListOf<RoutineItem>()

    val isEditing: Boolean get() = routineId > 0L

    /** [existingId] of 0 or less starts a new session. */
    fun load(existingId: Long) {
        if (loaded) return // survives rotation and re-entry from the picker
        loaded = true
        if (existingId <= 0L) {
            publish()
            return
        }
        viewModelScope.launch {
            val routine = routineRepository.getRoutine(existingId)
            if (routine == null) {
                _uiState.value = UiState.Error("That session no longer exists.")
                return@launch
            }
            routineId = routine.id
            name = routine.name
            icon = routine.icon
            items.clear()
            items += routine.items
            _events.emit(RoutineEditorEvent.Loaded(name, icon))
            publish()
        }
    }

    /**
     * Held without republishing: the field already shows what was typed, and
     * pushing it back would fight the cursor.
     */
    fun setName(value: String) {
        name = value
    }

    fun setIcon(value: String) {
        icon = value
    }

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch {
            val exercise = exerciseRepository.getExercise(exerciseId) ?: return@launch
            items += RoutineItem(exercise, DEFAULT_ITEM_MINUTES)
            publish()
        }
    }

    fun changeDuration(index: Int, deltaMinutes: Int) {
        val item = items.getOrNull(index) ?: return
        items[index] = item.copy(
            durationMinutes = (item.durationMinutes + deltaMinutes)
                .coerceIn(MIN_ITEM_MINUTES, MAX_ITEM_MINUTES)
        )
        publish()
    }

    fun removeItem(index: Int) {
        if (index !in items.indices) return
        items.removeAt(index)
        publish()
    }

    fun moveItem(index: Int, delta: Int) {
        val target = index + delta
        if (index !in items.indices || target !in items.indices) return
        items.add(target, items.removeAt(index))
        publish()
    }

    /** Returns false when the session is not complete enough to save. */
    fun save(currentName: String): Boolean {
        val trimmed = currentName.trim()
        if (trimmed.isEmpty() || items.isEmpty()) return false
        name = trimmed
        val snapshot = items.toList()
        viewModelScope.launch {
            if (routineId > 0L) {
                routineRepository.updateRoutine(routineId, name, icon, snapshot)
            } else {
                routineRepository.createRoutine(name, icon, snapshot)
            }
            _events.emit(RoutineEditorEvent.Saved)
        }
        return true
    }

    fun delete() {
        if (routineId <= 0L) return
        viewModelScope.launch {
            routineRepository.deleteRoutine(routineId)
            _events.emit(RoutineEditorEvent.Saved)
        }
    }

    private fun publish() {
        val draft = RoutineDraft(
            name = name,
            icon = icon,
            items = items.toList(),
            weightKg = weightKg,
            isEditing = routineId > 0L
        )
        _uiState.value = if (items.isEmpty()) UiState.Empty else UiState.Success(draft)
    }

    companion object {
        const val DEFAULT_ICON = "🏋️"
        const val DEFAULT_ITEM_MINUTES = 15
        const val ITEM_STEP_MINUTES = 5
        private const val MIN_ITEM_MINUTES = 5
        private const val MAX_ITEM_MINUTES = 300
    }
}
