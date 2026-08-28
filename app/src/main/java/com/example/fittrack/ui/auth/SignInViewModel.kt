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

/** Whether the form is collecting a new account or an existing one. */
enum class AuthMode { SIGN_IN, SIGN_UP }

data class SignInFormState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null
)

sealed class SignInEvent {
    /** Auth and the first sync are done; the app can open. */
    object Authenticated : SignInEvent()
    data class SyncWarning(val message: String) : SignInEvent()
}

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

    fun toggleMode() {
        _state.value = _state.value.copy(
            mode = if (_state.value.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN,
            error = null,
            notice = null
        )
    }

    fun submitEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "Enter your email and password.")
            return
        }
        if (_state.value.mode == AuthMode.SIGN_UP && password.length < MIN_PASSWORD) {
            _state.value = _state.value.copy(
                error = "Use a password of at least $MIN_PASSWORD characters."
            )
            return
        }
        run {
            val mode = _state.value.mode
            launchAuth {
                if (mode == AuthMode.SIGN_UP) {
                    authRepository.signUpWithEmail(email, password)
                } else {
                    authRepository.signInWithEmail(email, password)
                }
            }
        }
    }

    fun submitGoogleToken(idToken: String) {
        launchAuth { authRepository.signInWithGoogle(idToken) }
    }

    fun reportGoogleFailure(message: String) {
        _state.value = _state.value.copy(busy = false, error = message)
    }

    fun setBusy(busy: Boolean) {
        _state.value = _state.value.copy(busy = busy, error = null)
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            when (val outcome = authRepository.sendPasswordReset(email)) {
                is AuthOutcome.Success -> _state.value = _state.value.copy(
                    busy = false,
                    notice = "Password reset email sent."
                )
                is AuthOutcome.Failure -> _state.value = _state.value.copy(
                    busy = false,
                    error = outcome.message
                )
            }
        }
    }

    private fun launchAuth(block: suspend () -> AuthOutcome) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            when (val outcome = block()) {
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

    private companion object {
        const val MIN_PASSWORD = 6
    }
}
