package com.example.fittrack.ui.community

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.data.media.PostImageStore
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

data class ComposerState(
    val image: Uri? = null,
    val busy: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PostComposerViewModel @Inject constructor(
    private val repository: CommunityRepository,
    private val postImageStore: PostImageStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val communityId: String = savedStateHandle[PostComposerActivity.EXTRA_ID] ?: ""

    private val _state = MutableStateFlow(ComposerState())
    val state: StateFlow<ComposerState> = _state.asStateFlow()

    private val _posted = MutableSharedFlow<Unit>()
    val posted: SharedFlow<Unit> = _posted.asSharedFlow()

    init {
        // An upload that never finished left a prepared copy behind. Clearing
        // on the way in keeps the cache from growing without a background job.
        postImageStore.clearStale()
    }

    fun pickImage(uri: Uri?) {
        _state.value = _state.value.copy(image = uri)
    }

    fun post(text: String) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, error = null)

        viewModelScope.launch {
            repository.createPost(communityId, text, _state.value.image)
                .onSuccess {
                    _state.value = _state.value.copy(busy = false)
                    _posted.emit(Unit)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        busy = false,
                        error = it.message ?: "Could not post that."
                    )
                }
        }
    }
}
