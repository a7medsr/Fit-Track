package com.example.fittrack.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.domain.model.AuthOutcome
import com.example.fittrack.domain.model.Community
import com.example.fittrack.domain.repository.AuthRepository
import com.example.fittrack.domain.repository.CommunityRepository
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A row in the hub list: either a section heading or a community. */
sealed class HubRow {
    data class Header(val title: String) : HubRow()
    data class Item(val community: Community) : HubRow()
    data class Note(val text: String) : HubRow()
}

@HiltViewModel
class CommunityHubViewModel @Inject constructor(
    private val repository: CommunityRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<HubRow>>>(UiState.Loading)
    val state: StateFlow<UiState<List<HubRow>>> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var query: String = ""
    private var searchJob: Job? = null

    val isSignedIn: Boolean get() = authRepository.currentUser != null

    /** True when the account has no name yet, so posting anonymously is avoided. */
    val needsName: Boolean
        get() = authRepository.currentUser?.displayName.isNullOrBlank()

    init {
        load()
    }

    fun load() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runLoad() }
    }

    /**
     * Debounced: each keystroke would otherwise be two Firestore queries, and
     * typing a six-character code would cost a dozen reads to answer a question
     * only the last one asks.
     */
    fun search(text: String) {
        if (text == query) return
        query = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runLoad()
        }
    }

    private suspend fun runLoad() {
        _state.value = UiState.Loading

        if (!isSignedIn) {
            _state.value = UiState.Error("Sign in to join a community.")
            return
        }

        val searching = query.isNotBlank()
        val mine = if (searching) Result.success(emptyList()) else repository.myCommunities()
        val found = repository.discover(query)

        val failure = mine.exceptionOrNull() ?: found.exceptionOrNull()
        if (failure != null) {
            _state.value = UiState.Error(failure.message ?: "Could not load communities.")
            return
        }

        val myList = mine.getOrDefault(emptyList())
        val myIds = myList.map { it.id }.toSet()
        // A community the user is already in is shown once, under "Yours" --
        // seeing it again in the directory below invites them to join a group
        // they are already in.
        val discovered = found.getOrDefault(emptyList()).filter { it.id !in myIds }

        val rows = buildList {
            if (searching) {
                if (discovered.isEmpty()) {
                    add(HubRow.Note("Nothing matched \"$query\". Try the community's code."))
                } else {
                    add(HubRow.Header("Results"))
                    discovered.forEach { add(HubRow.Item(it)) }
                }
            } else {
                if (myList.isEmpty()) {
                    add(HubRow.Note("You're not in a community yet. Join one below, or create your own."))
                } else {
                    add(HubRow.Header("Your communities"))
                    myList.forEach { add(HubRow.Item(it)) }
                }
                if (discovered.isNotEmpty()) {
                    add(HubRow.Header("Discover"))
                    discovered.forEach { add(HubRow.Item(it)) }
                }
            }
        }

        _state.value = if (rows.isEmpty()) UiState.Empty else UiState.Success(rows)
    }

    /**
     * Names the account, then runs whatever the user was trying to do when we
     * stopped them. Stored on the Firebase account itself, so it reaches their
     * other devices without going through the sync mirror.
     */
    fun saveName(displayName: String, then: () -> Unit) {
        viewModelScope.launch {
            when (val outcome = authRepository.updateDisplayName(displayName)) {
                is AuthOutcome.Success -> then()
                is AuthOutcome.Failure -> _messages.emit(outcome.message)
            }
        }
    }

    fun requestToJoin(community: Community) {
        viewModelScope.launch {
            repository.requestToJoin(community.id)
                .onSuccess {
                    _messages.emit("Request sent. The admin of ${community.name} has to approve it.")
                    runLoad()
                }
                .onFailure { _messages.emit(it.message ?: "Could not send that request.") }
        }
    }

    fun withdrawRequest(community: Community) {
        viewModelScope.launch {
            repository.withdrawRequest(community.id)
                .onSuccess {
                    _messages.emit("Request withdrawn.")
                    runLoad()
                }
                .onFailure { _messages.emit(it.message ?: "Could not withdraw that request.") }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
