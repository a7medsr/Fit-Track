package com.example.fittrack.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.data.prefs.GoalPreferences
import com.example.fittrack.data.sensor.StepSensorManager
import com.example.fittrack.domain.model.DailySteps
import com.example.fittrack.domain.model.AuthUser
import com.example.fittrack.domain.repository.AuthRepository
import com.example.fittrack.domain.repository.AvatarRepository
import com.example.fittrack.domain.repository.AvatarResult
import com.example.fittrack.domain.repository.SyncRepository
import com.example.fittrack.domain.repository.StepRepository
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val stepSensorManager: StepSensorManager,
    private val stepRepository: StepRepository,
    private val goalPreferences: GoalPreferences,
    private val authRepository: AuthRepository,
    private val avatarRepository: AvatarRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    /**
     * The cached avatar, carrying a revision.
     *
     * The filename is stable per user, so a plain File would compare equal
     * after the picture is replaced and StateFlow would drop the update --
     * overwriting your photo would silently leave the old one on screen. The
     * revision guarantees every change is emitted.
     */
    private var avatarRevision = 0L
    private val _avatar = MutableStateFlow(
        avatarRepository.localAvatar()?.let { LocalAvatar(it, ++avatarRevision) }
    )
    val avatar: StateFlow<LocalAvatar?> = _avatar.asStateFlow()

    /** One-shot feedback after a picture is chosen. */
    private val _avatarMessage = MutableSharedFlow<AvatarMessage>()
    val avatarMessage: SharedFlow<AvatarMessage> = _avatarMessage.asSharedFlow()

    init {
        // Fires once immediately, then again after each pull. A second device
        // learns the URL from the profile sync, which may land after this
        // screen is already open, so a one-shot check would miss it.
        viewModelScope.launch {
            syncRepository.lastPullAt.collect {
                avatarRepository.refreshFromRemote()?.let { file ->
                    _avatar.value = LocalAvatar(file, ++avatarRevision)
                }
            }
        }
    }

    fun pickedAvatar(uri: android.net.Uri) {
        viewModelScope.launch {
            when (val result = avatarRepository.setAvatar(uri)) {
                is AvatarResult.Success -> {
                    _avatar.value = LocalAvatar(result.file, ++avatarRevision)
                    _avatarMessage.emit(
                        when {
                            result.remoteUrl != null -> AvatarMessage.Uploaded
                            authRepository.currentUser == null -> AvatarMessage.LocalOnly
                            else -> AvatarMessage.UploadFailed
                        }
                    )
                }
                is AvatarResult.Failure ->
                    _avatarMessage.emit(AvatarMessage.Failed(result.message))
            }
        }
    }

    val currentUser: AuthUser? get() = authRepository.currentUser

    fun signOut() = authRepository.signOut()

    private val _uiState = MutableStateFlow<UiState<DailySteps>>(UiState.Loading)
    val uiState: StateFlow<UiState<DailySteps>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            stepSensorManager.observeSteps()
                .catch { e -> _uiState.value = UiState.Error(e.message ?: "Sensor error") }
                .collect { rawValue ->
                    val daily = stepRepository.syncTodaySteps(rawValue)
                    _uiState.value = UiState.Success(daily.copy(goal = goalPreferences.getGoal()))
                }
        }
    }

    fun updateGoal(newGoal: Int) {
        goalPreferences.setGoal(newGoal)
        val current = _uiState.value
        if (current is UiState.Success) {
            _uiState.value = UiState.Success(current.data.copy(goal = newGoal))
        }
    }

}

/** A cached avatar file plus a revision, so replacing it always re-renders. */
data class LocalAvatar(val file: java.io.File, val revision: Long)

/** What to tell the user after they pick a picture. */
sealed class AvatarMessage {
    object Uploaded : AvatarMessage()
    object LocalOnly : AvatarMessage()
    object UploadFailed : AvatarMessage()
    data class Failed(val message: String) : AvatarMessage()
}
