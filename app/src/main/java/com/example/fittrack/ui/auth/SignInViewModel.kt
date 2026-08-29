package com.example.fittrack.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fittrack.domain.model.AuthOutcome
import com.example.fittrack.domain.repository.AuthRepository
import com.example.fittrack.domain.repository.SyncRepository
import com.example.fittrack.domain.repository.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInFormState(
    val busy: Boolean = false,
    val error: String? = null
)

sealed class SignInEvent {
    /** Auth and the first sync are done; the app can open. */
    object Authenticated : SignInEvent()
    data class SyncWarning(val message: String) : SignInEvent()
}

/**
 * Google is the only way in, so there is no form to validate and no sign-up
 * mode: the first Google sign-in creates the account, and every one after it
 * signs into the same one.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SignInFormState())
    val state: StateFlow<SignInFormState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SignInEvent>()
    val events: SharedFlow<SignInEvent> = _events.asSharedFlow()

    val alreadySignedIn: Boolean get() = authRepository.currentUser != null

    fun submitGoogleToken(idToken: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)

            when (val outcome = authRepository.signInWithGoogle(idToken)) {
                is AuthOutcome.Failure ->
                    _state.value = _state.value.copy(busy = false, error = outcome.message)

                is AuthOutcome.Success -> {
                    // Merge whatever is already on this device into the account
                    // before letting the app open, so the first screen is right.
                    when (val sync = syncRepository.syncOnSignIn()) {
                        is SyncResult.Failure ->
                            _events.emit(SignInEvent.SyncWarning(sync.message))
                        else -> Unit
                    }
                    _state.value = _state.value.copy(busy = false)
                    _events.emit(SignInEvent.Authenticated)
                }
            }
        }
    }

    fun reportGoogleFailure(message: String) {
        _state.value = _state.value.copy(busy = false, error = message)
    }

    fun setBusy(busy: Boolean) {
        _state.value = _state.value.copy(busy = busy, error = null)
    }
}
