package com.example.hastatucasa.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hastatucasa.data.model.UserRole
import com.example.hastatucasa.data.repository.FirebaseAuthRepository
import com.example.hastatucasa.data.repository.FirebaseMessagingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── State ────────────────────────────────────────────────────────────────────

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val errorMessage: String? = null,
    val isSignUpMode: Boolean = false,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

/**
 * Shared ViewModel for both the shopper and deliverer auth screens.
 *
 * The [role] is determined by the flavor's entry point:
 *  - Shopper MainActivity passes [UserRole.SHOPPER]
 *  - Deliverer MainActivity passes [UserRole.DELIVERER]
 *
 * Pass the role via [SavedStateHandle] or as a factory parameter once
 * you add a proper auth screen to each flavor's NavHost.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: FirebaseAuthRepository,
    private val messagingRepository: FirebaseMessagingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(isSignedIn = authRepository.isSignedIn)
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // ─── Intents ──────────────────────────────────────────────────────────────

    fun onSignIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            authRepository.signIn(email, password)
                .onSuccess {
                    // Persist FCM token now that we have a uid
                    messagingRepository.refreshAndPersistToken()
                    _uiState.value = _uiState.value.copy(isLoading = false, isSignedIn = true)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Sign-in failed",
                    )
                }
        }
    }

    fun onSignUp(email: String, password: String, role: UserRole) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            authRepository.signUp(email, password, role)
                .onSuccess {
                    messagingRepository.refreshAndPersistToken()
                    _uiState.value = _uiState.value.copy(isLoading = false, isSignedIn = true)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Sign-up failed",
                    )
                }
        }
    }

    fun onSignOut() {
        authRepository.signOut()
        _uiState.value = _uiState.value.copy(isSignedIn = false)
    }

    fun onToggleMode() {
        _uiState.value = _uiState.value.copy(
            isSignUpMode = !_uiState.value.isSignUpMode,
            errorMessage = null,
        )
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}