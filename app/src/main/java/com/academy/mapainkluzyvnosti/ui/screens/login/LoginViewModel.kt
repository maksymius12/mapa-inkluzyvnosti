package com.academy.mapainkluzyvnosti.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.data.repository.ProfileStatus
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Куди вести після успішного входу. */
enum class AuthDestination { MAP, ROLE_SETUP }

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val destination: AuthDestination? = null,
    val isForgotPasswordDialogOpen: Boolean = false,
    val forgotPasswordEmail: String = "",
    val isSendingResetEmail: Boolean = false,
    val resetPasswordMessage: String? = null
)

/** Вхід: email/пароль, Google, гість. Жодних питань про мету й вікову категорію. */
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun submitEmailForm() {
        val state = _uiState.value
        val email = state.email.trim()
        val emailError = if (!isValidEmail(email)) "Введіть коректний email" else null
        val passwordError = if (state.password.isEmpty()) "Введіть пароль" else null
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        launchSignIn { authRepository.signInWithEmail(email, state.password) }
    }

    fun signInWithGoogleIdToken(idToken: String) {
        launchSignIn { authRepository.signInWithGoogleIdToken(idToken) }
    }

    /** Гостьовий режим — локальний прапорець, без жодного звернення до Supabase Auth. */
    fun continueAsGuest() {
        demoModeStore.enable()
        _uiState.update { it.copy(destination = AuthDestination.MAP) }
    }

    fun consumeDestination() {
        _uiState.update { it.copy(destination = null) }
    }

    fun openForgotPasswordDialog() {
        _uiState.update { it.copy(isForgotPasswordDialogOpen = true, forgotPasswordEmail = it.email, resetPasswordMessage = null) }
    }

    fun dismissForgotPasswordDialog() {
        _uiState.update { it.copy(isForgotPasswordDialogOpen = false) }
    }

    fun onForgotPasswordEmailChange(value: String) {
        _uiState.update { it.copy(forgotPasswordEmail = value) }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.forgotPasswordEmail.trim()
        if (!isValidEmail(email)) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingResetEmail = true) }
            runCatching { authRepository.resetPasswordForEmail(email) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSendingResetEmail = false,
                            isForgotPasswordDialogOpen = false,
                            resetPasswordMessage = "Лист для відновлення пароля надіслано"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSendingResetEmail = false, errorMessage = e.toUserMessage("Не вдалося надіслати лист. Спробуйте ще раз"))
                    }
                }
        }
    }

    fun consumeResetPasswordMessage() {
        _uiState.update { it.copy(resetPasswordMessage = null) }
    }

    /** Після входу профіль лише читається; мету й вік питаємо тільки при першому вході через Google. */
    private fun launchSignIn(signIn: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                signIn()
                authRepository.loadProfileStatus()
            }.onSuccess { status ->
                demoModeStore.disable()
                val destination = when (status) {
                    is ProfileStatus.Ready -> {
                        currentUserStore.set(status.user)
                        AuthDestination.MAP
                    }
                    is ProfileStatus.NeedsRoles -> AuthDestination.ROLE_SETUP
                }
                _uiState.update { it.copy(isLoading = false, destination = destination) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.toUserMessage("Не вдалося увійти. Спробуйте ще раз")) }
            }
        }
    }
}
