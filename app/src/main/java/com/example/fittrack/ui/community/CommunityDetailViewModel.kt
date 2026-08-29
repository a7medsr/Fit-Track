package com.example.fittrack.ui.community

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.domain.model.Community
import com.example.fittrack.domain.model.CommunityLimits
import com.example.fittrack.domain.model.CommunityPost
import com.example.fittrack.domain.model.Reaction
import com.example.fittrack.domain.repository.CommunityRepository
import com.example.fittrack.domain.repository.Leaderboard
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

enum class CommunityTab { FEED, LEADERBOARD }

sealed class DetailEvent {
    data class Message(val text: String) : DetailEvent()
    /** The user is no longer in this community, so the screen has to close. */
    object Closed : DetailEvent()
}

@HiltViewModel
class CommunityDetailViewModel @Inject constructor(
    private val repository: CommunityRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val communityId: String = savedStateHandle[CommunityDetailActivity.EXTRA_ID] ?: ""

    private val _community = MutableStateFlow<Community?>(null)
    val community: StateFlow<Community?> = _community.asStateFlow()

    private val _tab = MutableStateFlow(CommunityTab.FEED)
    val tab: StateFlow<CommunityTab> = _tab.asStateFlow()

    private val _feed = MutableStateFlow<UiState<List<CommunityPost>>>(UiState.Loading)
    val feed: StateFlow<UiState<List<CommunityPost>>> = _feed.asStateFlow()

    private val _board = MutableStateFlow<UiState<Leaderboard>>(UiState.Loading)
    val board: StateFlow<UiState<Leaderboard>> = _board.asStateFlow()

    private val _events = MutableSharedFlow<DetailEvent>()
    val events: SharedFlow<DetailEvent> = _events.asSharedFlow()

    private var loadingMore = false
    private var reachedEnd = false

    init {
        refresh()
    }

    fun switchTab(tab: CommunityTab) {
        if (_tab.value == tab) return
        _tab.value = tab
        if (tab == CommunityTab.LEADERBOARD) loadBoard()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.community(communityId)
                .onSuccess { community ->
                    _community.value = community
                    if (!community.isMember) {
                        // Removed by the admin while the screen was open, or
                        // opened from a stale list. Either way there is nothing
                        // here they are allowed to read.
                        _events.emit(DetailEvent.Closed)
                        return@onSuccess
                    }
                    loadFeed()
                    if (_tab.value == CommunityTab.LEADERBOARD) loadBoard()
                    publishScore()
                }
                .onFailure {
                    _feed.value = UiState.Error(it.message ?: "Could not open that community.")
                }
        }
    }

    private fun loadFeed() {
        reachedEnd = false
        viewModelScope.launch {
            _feed.value = UiState.Loading
            repository.posts(communityId)
                .onSuccess { posts ->
                    reachedEnd = posts.size < CommunityLimits.PAGE_SIZE
                    _feed.value = if (posts.isEmpty()) UiState.Empty else UiState.Success(posts)
                }
                .onFailure { _feed.value = UiState.Error(it.message ?: "Could not load posts.") }
        }
    }

    /**
     * Paged rather than loaded whole: a community that has been going a while
     * would otherwise cost hundreds of reads and a long wait every time anyone
     * opens it.
     */
    fun loadMore() {
        val current = (_feed.value as? UiState.Success)?.data ?: return
        if (loadingMore || reachedEnd || current.isEmpty()) return
        loadingMore = true

        viewModelScope.launch {
            repository.posts(communityId, current.last().createdAt)
                .onSuccess { more ->
                    reachedEnd = more.size < CommunityLimits.PAGE_SIZE
                    if (more.isNotEmpty()) _feed.value = UiState.Success(current + more)
                }
                .onFailure { _events.emit(DetailEvent.Message(it.message ?: "Could not load more.")) }
            loadingMore = false
        }
    }

    private fun loadBoard() {
        viewModelScope.launch {
            _board.value = UiState.Loading
            repository.leaderboard(communityId)
                .onSuccess { _board.value = UiState.Success(it) }
                .onFailure {
                    _board.value = UiState.Error(it.message ?: "Could not load the leaderboard.")
                }
        }
    }

    /**
     * Publishes this device's weekly number on the way in.
     *
     * Silent on failure: it is a background courtesy, and a toast about a score
     * the user did not ask to upload would only be noise.
     */
    private fun publishScore() {
        viewModelScope.launch {
            repository.publishMyScore(communityId)
                .onSuccess { if (_tab.value == CommunityTab.LEADERBOARD) loadBoard() }
        }
    }

    fun react(post: CommunityPost, reaction: Reaction) {
        // Applied locally first so the tap feels instant; the reload after the
        // write is what makes the count correct rather than merely optimistic.
        val current = (_feed.value as? UiState.Success)?.data ?: return
        val clearing = post.myReaction == reaction
        _feed.value = UiState.Success(
            current.map { if (it.id == post.id) it.withLocalReaction(reaction, clearing) else it }
        )

        viewModelScope.launch {
            repository.react(communityId, post.id, if (clearing) null else reaction)
                .onFailure {
                    _events.emit(DetailEvent.Message(it.message ?: "Could not save that."))
                    loadFeed()
                }
        }
    }

    fun deletePost(post: CommunityPost) {
        viewModelScope.launch {
            repository.deletePost(communityId, post)
                .onSuccess {
                    val current = (_feed.value as? UiState.Success)?.data.orEmpty()
                    val remaining = current.filterNot { it.id == post.id }
                    _feed.value = if (remaining.isEmpty()) UiState.Empty else UiState.Success(remaining)
                    _events.emit(DetailEvent.Message("Post deleted."))
                }
                .onFailure { _events.emit(DetailEvent.Message(it.message ?: "Could not delete that.")) }
        }
    }

    fun leave() {
        viewModelScope.launch {
            repository.leave(communityId)
                .onSuccess {
                    _events.emit(DetailEvent.Message("You left this community."))
                    _events.emit(DetailEvent.Closed)
                }
                .onFailure { _events.emit(DetailEvent.Message(it.message ?: "Could not leave.")) }
        }
    }

    private fun CommunityPost.withLocalReaction(
        reaction: Reaction,
        clearing: Boolean
    ): CommunityPost {
        val counts = reactionCounts.toMutableMap()
        myReaction?.let { previous ->
            val left = (counts[previous] ?: 0) - 1
            if (left > 0) counts[previous] = left else counts.remove(previous)
        }
        if (!clearing) counts[reaction] = (counts[reaction] ?: 0) + 1
        return copy(reactionCounts = counts, myReaction = if (clearing) null else reaction)
    }
}
