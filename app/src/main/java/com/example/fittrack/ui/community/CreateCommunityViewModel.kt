package com.example.fittrack.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.domain.model.CommunityMetric
import com.example.fittrack.domain.repository.CommunityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateCommunityState(
    val icon: String = ICONS.first(),
    val metric: CommunityMetric = CommunityMetric.DEFAULT,
    val busy: Boolean = false,
    val error: String? = null
)

sealed class CreateCommunityEvent {
    /** Carries the new code so the screen can show it to the creator. */
    data class Created(val communityId: String, val name: String) : CreateCommunityEvent()
}

/** A small fixed palette, so no emoji keyboard is needed to pick one. */
val ICONS = listOf("🏋️", "🏃", "🚴", "🧘", "⚽", "🥊", "🏊", "💪", "🔥", "🏆")

@HiltViewModel
class CreateCommunityViewModel @Inject constructor(
    private val repository: CommunityRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateCommunityState())
    val state: StateFlow<CreateCommunityState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CreateCommunityEvent>()
    val events: SharedFlow<CreateCommunityEvent> = _events.asSharedFlow()

    fun pickIcon(icon: String) {
        _state.value = _state.value.copy(icon = icon)
    }

    fun pickMetric(metric: CommunityMetric) {
        _state.value = _state.value.copy(metric = metric)
    }

    fun create(name: String, description: String) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, error = null)

        viewModelScope.launch {
            repository.create(name, description, _state.value.icon, _state.value.metric)
                .onSuccess {
                    _state.value = _state.value.copy(busy = false)
                    _events.emit(CreateCommunityEvent.Created(it.id, it.name))
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        busy = false,
                        error = it.message ?: "Could not create that community."
                    )
                }
        }
    }
}
