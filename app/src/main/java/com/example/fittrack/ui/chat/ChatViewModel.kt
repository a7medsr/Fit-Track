package com.example.fittrack.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.data.ai.ActionExecutor
import com.example.fittrack.domain.model.ActionState
import com.example.fittrack.domain.model.ChatMessage
import com.example.fittrack.domain.repository.ChatError
import com.example.fittrack.domain.repository.ChatRepository
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The list plus whether a reply is in flight, so the UI can show a typing row. */
data class ChatScreenState(
    val messages: List<ChatMessage>,
    val awaitingReply: Boolean,
    /**
     * Confirm-chip wording per message id. Built here because resolving an
     * exercise name is a suspending lookup and an adapter cannot wait.
     */
    val confirmPrompts: Map<Long, String> = emptyMap()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val actionExecutor: ActionExecutor
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ChatScreenState>>(UiState.Loading)
    val uiState: StateFlow<UiState<ChatScreenState>> = _uiState.asStateFlow()

    private val _errors = MutableSharedFlow<ChatError>()
    val errors: SharedFlow<ChatError> = _errors.asSharedFlow()

    private val awaitingReply = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            combine(chatRepository.observeMessages(), awaitingReply) { messages, busy ->
                val prompts = messages
                    .filter { it.actionState == ActionState.PENDING && it.pendingAction != null }
                    .associate { it.id to actionExecutor.describe(it.pendingAction!!) }
                ChatScreenState(messages, busy, prompts)
            }
                .catch { e ->
                    _uiState.value = UiState.Error(e.message ?: "Couldn't load the conversation")
                }
                .collect { state ->
                    _uiState.value = if (state.messages.isEmpty() && !state.awaitingReply) {
                        UiState.Empty
                    } else {
                        UiState.Success(state)
                    }
                }
        }
    }

    fun send(text: String) {
        if (text.isBlank() || awaitingReply.value) return
        viewModelScope.launch {
            awaitingReply.value = true
            val error = chatRepository.send(text)
            awaitingReply.value = false
            error?.let { _errors.emit(it) }
        }
    }

    fun confirm(messageId: Long) {
        viewModelScope.launch {
            awaitingReply.value = true
            val error = chatRepository.confirmAction(messageId)
            awaitingReply.value = false
            error?.let { _errors.emit(it) }
        }
    }

    fun cancel(messageId: Long) {
        viewModelScope.launch { chatRepository.cancelAction(messageId) }
    }

    fun clearHistory() {
        viewModelScope.launch { chatRepository.clearHistory() }
    }

}
