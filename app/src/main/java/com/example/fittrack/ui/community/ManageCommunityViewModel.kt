package com.example.fittrack.ui.community

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.domain.model.Community
import com.example.fittrack.domain.model.CommunityMember
import com.example.fittrack.domain.model.CommunityMetric
import com.example.fittrack.domain.model.JoinRequest
import com.example.fittrack.domain.repository.CommunityRepository
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

data class ManageState(
    val community: Community,
    val requests: List<JoinRequest>,
    val members: List<CommunityMember>,
    val banned: List<CommunityMember>
)

sealed class ManageEvent {
    data class Message(val text: String) : ManageEvent()
    /** The community is gone, or this user is no longer its admin. */
    object Closed : ManageEvent()
}

@HiltViewModel
class ManageCommunityViewModel @Inject constructor(
    private val repository: CommunityRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val communityId: String = savedStateHandle[ManageCommunityActivity.EXTRA_ID] ?: ""

    private val _state = MutableStateFlow<UiState<ManageState>>(UiState.Loading)
    val state: StateFlow<UiState<ManageState>> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ManageEvent>()
    val events: SharedFlow<ManageEvent> = _events.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading

            val community = repository.community(communityId).getOrElse {
                _state.value = UiState.Error(it.message ?: "Could not load this community.")
                return@launch
            }
            if (!community.isAdmin) {
                // Admin was handed over from another device, or this screen was
                // reached from a stale list. Nothing below is theirs to change.
                _events.emit(ManageEvent.Closed)
                return@launch
            }

            val requests = repository.pendingRequests(communityId).getOrDefault(emptyList())
            val members = repository.members(communityId).getOrDefault(emptyList())
            val banned = repository.bannedMembers(communityId).getOrDefault(emptyList())

            _state.value = UiState.Success(ManageState(community, requests, members, banned))
        }
    }

    fun approve(request: JoinRequest) = act("${request.name} is in.") {
        repository.approve(communityId, request.uid)
    }

    fun reject(request: JoinRequest) = act("Request rejected.") {
        repository.reject(communityId, request.uid)
    }

    fun remove(member: CommunityMember) = act("${member.name} was removed and can't rejoin.") {
        repository.removeMember(communityId, member.uid)
    }

    fun unban(member: CommunityMember) = act("They can request to join again.") {
        repository.unban(communityId, member.uid)
    }

    fun setMetric(metric: CommunityMetric) = act("Now ranking on ${metric.displayName}.") {
        repository.setMetric(communityId, metric)
    }

    fun transferAdmin(member: CommunityMember) {
        viewModelScope.launch {
            repository.transferAdmin(communityId, member.uid)
                .onSuccess {
                    _events.emit(ManageEvent.Message("${member.name} now runs this community."))
                    // No longer the admin, so this screen is no longer theirs.
                    _events.emit(ManageEvent.Closed)
                }
                .onFailure { _events.emit(ManageEvent.Message(it.message ?: "Could not transfer.")) }
        }
    }

    fun deleteCommunity() {
        viewModelScope.launch {
            repository.deleteCommunity(communityId)
                .onSuccess {
                    _events.emit(ManageEvent.Message("Community deleted."))
                    _events.emit(ManageEvent.Closed)
                }
                .onFailure { _events.emit(ManageEvent.Message(it.message ?: "Could not delete.")) }
        }
    }

    private fun act(success: String, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            block()
                .onSuccess {
                    _events.emit(ManageEvent.Message(success))
                    load()
                }
                .onFailure { _events.emit(ManageEvent.Message(it.message ?: "That didn't work.")) }
        }
    }
}
