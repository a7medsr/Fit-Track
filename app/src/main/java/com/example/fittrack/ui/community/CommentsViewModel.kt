package com.example.fittrack.ui.community

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.domain.model.CommunityLimits
import com.example.fittrack.domain.model.PostComment
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

@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val repository: CommunityRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val communityId: String = savedStateHandle[CommentsActivity.EXTRA_COMMUNITY] ?: ""
    private val postId: String = savedStateHandle[CommentsActivity.EXTRA_POST] ?: ""

    private val _state = MutableStateFlow<UiState<List<PostComment>>>(UiState.Loading)
    val state: StateFlow<UiState<List<PostComment>>> = _state.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var loadingMore = false
    private var reachedEnd = false

    init {
        load()
    }

    fun load() {
        reachedEnd = false
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.comments(communityId, postId)
                .onSuccess { comments ->
                    reachedEnd = comments.size < CommunityLimits.PAGE_SIZE
                    // Newest first from Firestore, reversed for reading: a
                    // conversation runs down the screen.
                    _state.value = if (comments.isEmpty()) UiState.Empty
                    else UiState.Success(comments.reversed())
                }
                .onFailure {
                    _state.value = UiState.Error(it.message ?: "Could not load comments.")
                }
        }
    }

    /** Older comments, fetched when the user scrolls back up to the top. */
    fun loadOlder() {
        val current = (_state.value as? UiState.Success)?.data ?: return
        if (loadingMore || reachedEnd || current.isEmpty()) return
        loadingMore = true

        viewModelScope.launch {
            repository.comments(communityId, postId, current.first().createdAt)
                .onSuccess { older ->
                    reachedEnd = older.size < CommunityLimits.PAGE_SIZE
                    if (older.isNotEmpty()) {
                        _state.value = UiState.Success(older.reversed() + current)
                    }
                }
            loadingMore = false
        }
    }

    fun send(text: String) {
        if (_sending.value) return
        _sending.value = true

        viewModelScope.launch {
            repository.addComment(communityId, postId, text)
                .onSuccess { load() }
                .onFailure { _messages.emit(it.message ?: "Could not post that comment.") }
            _sending.value = false
        }
    }

    fun delete(comment: PostComment) {
        viewModelScope.launch {
            repository.deleteComment(communityId, postId, comment.id)
                .onSuccess {
                    val remaining = (_state.value as? UiState.Success)?.data.orEmpty()
                        .filterNot { it.id == comment.id }
                    _state.value = if (remaining.isEmpty()) UiState.Empty
                    else UiState.Success(remaining)
                }
                .onFailure { _messages.emit(it.message ?: "Could not delete that comment.") }
        }
    }
}
