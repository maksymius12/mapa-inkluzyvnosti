package com.academy.mapainkluzyvnosti.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthFormMode { SIGN_IN, SIGN_UP }

data class LoginUiState(
    val purposeRole: UserPurposeRole = UserPurposeRole.RESIDENT,
    val ageGroup: UserAgeGroup = UserAgeGroup.ADULT,
    val formMode: AuthFormMode = AuthFormMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val isForgotPasswordDialogOpen: Boolean = false,
    val forgotPasswordEmail: String = "",
    val isSendingResetEmail: Boolean = false,
    val resetPasswordMessage: String? = null
) {
    val formTitle: String get() = if (formMode == AuthFormMode.SIGN_IN) "Увійти" else "Зареєструватись"
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun setPurposeRole(role: UserPurposeRole) {
        _uiState.update { it.copy(purposeRole = role) }
    }

    fun setAgeGroup(group: UserAgeGroup) {
        _uiState.update { it.copy(ageGroup = group) }
    }

    fun setFormMode(mode: AuthFormMode) {
        _uiState.update { it.copy(formMode = mode, errorMessage = null, emailError = null, passwordError = null) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun submitEmailForm() {
        val state = _uiState.value
        val emailError = if (!state.email.contains("@")) "Введи коректний email" else null
        val passwordError = if (state.password.length < 6) "Пароль має містити щонайменше 6 символів" else null
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        when (state.formMode) {
            AuthFormMode.SIGN_IN -> launchSignIn { authRepository.signInWithEmail(state.email, state.password) }
            AuthFormMode.SIGN_UP -> launchSignIn { authRepository.signUpWithEmail(state.email, state.password) }
        }
    }

    fun signInWithGoogleIdToken(idToken: String) {
        launchSignIn { authRepository.signInWithGoogleIdToken(idToken) }
    }

    /** Гостьовий режим — локальний прапорець, без жодного звернення до Supabase Auth. */
    fun continueAsGuest() {
        demoModeStore.enable()
        _uiState.update { it.copy(isLoggedIn = true) }
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
        val email = _uiState.value.forgotPasswordEmail
        if (!email.contains("@")) return
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
                    _uiState.update { it.copy(isSendingResetEmail = false, errorMessage = mapAuthError(e)) }
                }
        }
    }

    fun consumeResetPasswordMessage() {
        _uiState.update { it.copy(resetPasswordMessage = null) }
    }

    private fun launchSignIn(signIn: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                signIn()
                val userId = requireNotNull(authRepository.currentUserId) { "Немає активної сесії" }
                val state = _uiState.value
                val profile = authRepository.fetchOrCreateProfile(
                    userId = userId,
                    defaultName = "Користувач",
                    defaultPurposeRole = state.purposeRole,
                    defaultAgeGroup = state.ageGroup
                )
                authRepository.updateProfileRoles(userId, state.purposeRole, state.ageGroup)
                currentUserStore.set(profile.copy(purposeRole = state.purposeRole, ageGroup = state.ageGroup))
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = mapAuthError(e)) }
            }
        }
    }

    private fun mapAuthError(e: Throwable): String = when (e) {
        is AuthRestException -> when (e.errorCode) {
            AuthErrorCode.InvalidCredentials -> "Невірний email або пароль"
            AuthErrorCode.UserNotFound -> "Акаунта з таким email не знайдено"
            AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists -> "Акаунт з такою поштою вже існує"
            AuthErrorCode.EmailNotConfirmed -> "Підтвердь email перед входом — перевір пошту"
            AuthErrorCode.WeakPassword -> "Пароль занадто простий"
            else -> e.errorDescription
        }
        else -> e.message ?: "Помилка входу"
    }
}
