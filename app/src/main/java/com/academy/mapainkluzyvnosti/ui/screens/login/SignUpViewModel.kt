package com.academy.mapainkluzyvnosti.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.AppUser
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.data.remote.EMAIL_ALREADY_REGISTERED
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.data.repository.ProfileStatus
import com.academy.mapainkluzyvnosti.data.repository.SignUpResult
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val NAME_MIN = 2
private const val NAME_MAX = 50

enum class SignUpStep { ACCOUNT, ROLES }

data class SignUpUiState(
    val step: SignUpStep = SignUpStep.ACCOUNT,
    // Крок 1
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val acceptedTerms: Boolean = false,
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val passwordConfirmError: String? = null,
    val termsError: String? = null,
    // Крок 2
    val purposeRole: UserPurposeRole = UserPurposeRole.RESIDENT,
    val ageGroup: UserAgeGroup = UserAgeGroup.ADULT,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    /** Код надіслано на цю пошту — відкрити екран «Введіть код» (одноразова подія). */
    val codeSentTo: String? = null,
    /** Сесія вже є (підтвердження пошти вимкнене) — на мапу. */
    val isSignedIn: Boolean = false
)

/**
 * Реєстрація: крок 1 — ім'я, email, пароль, згода; крок 2 — мета й вікова категорія.
 * Профіль створює сервер з user metadata. Якщо Supabase вимагає підтвердження пошти,
 * далі — екран введення 6-значного коду (OtpViewModel).
 */
class SignUpViewModel(
    private val authRepository: AuthRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value.take(NAME_MAX + 10), nameError = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, passwordError = null) }
    fun onPasswordConfirmChange(value: String) = _uiState.update { it.copy(passwordConfirm = value, passwordConfirmError = null) }
    fun onTermsChange(value: Boolean) = _uiState.update { it.copy(acceptedTerms = value, termsError = null) }
    fun onPurposeRoleChange(value: UserPurposeRole) = _uiState.update { it.copy(purposeRole = value) }
    fun onAgeGroupChange(value: UserAgeGroup) = _uiState.update { it.copy(ageGroup = value) }

    /** Крок 1 -> крок 2 після перевірки полів. */
    fun next() {
        val state = _uiState.value
        val name = state.name.trim()
        val nameError = when {
            name.length < NAME_MIN -> "Вкажіть ім'я (щонайменше $NAME_MIN символи)"
            name.length > NAME_MAX -> "Ім'я має містити не більше $NAME_MAX символів"
            else -> null
        }
        val emailError = if (!isValidEmail(state.email)) "Введіть коректний email" else null
        val passwordError = passwordRuleError(state.password)
        val confirmError = passwordConfirmError(state.password, state.passwordConfirm)
        val termsError = if (!state.acceptedTerms) "Потрібна згода з умовами користування" else null
        if (listOf(nameError, emailError, passwordError, confirmError, termsError).any { it != null }) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    emailError = emailError,
                    passwordError = passwordError,
                    passwordConfirmError = confirmError,
                    termsError = termsError
                )
            }
            return
        }
        _uiState.update { it.copy(step = SignUpStep.ROLES, errorMessage = null) }
    }

    /** «Назад»: з кроку 2 — на крок 1. Повертає false, якщо назад уже нікуди (крок 1). */
    fun back(): Boolean {
        if (_uiState.value.isSubmitting) return true
        return when (_uiState.value.step) {
            SignUpStep.ROLES -> {
                _uiState.update { it.copy(step = SignUpStep.ACCOUNT, errorMessage = null) }
                true
            }
            SignUpStep.ACCOUNT -> false
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val email = state.email.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            runCatching {
                authRepository.signUpWithEmail(
                    email = email,
                    password = state.password,
                    name = state.name.trim(),
                    purposeRole = state.purposeRole,
                    ageGroup = state.ageGroup
                )
            }.onSuccess { result ->
                when (result) {
                    SignUpResult.SignedIn -> finishSignedIn(state)
                    // «Змінити пошту» з екрана коду повертає сюди — одразу на крок 1 із заповненими полями.
                    is SignUpResult.ConfirmationRequired -> _uiState.update {
                        it.copy(isSubmitting = false, step = SignUpStep.ACCOUNT, codeSentTo = result.email)
                    }
                    SignUpResult.AlreadyRegistered -> showEmailTaken()
                }
            }.onFailure { e -> handleSignUpError(e) }
        }
    }

    fun consumeCodeSent() = _uiState.update { it.copy(codeSentTo = null) }

    private suspend fun finishSignedIn(state: SignUpUiState) {
        val user = runCatching { authRepository.loadProfileStatus() }.getOrNull()
            .let { it as? ProfileStatus.Ready }?.user
            // Профіль ще не прочитався (мережа) — вибір кроку 2 відомий, тримаємо його локально.
            ?: AppUser(
                id = authRepository.currentUserId.orEmpty(),
                name = state.name.trim(),
                purposeRole = state.purposeRole,
                ageGroup = state.ageGroup
            )
        currentUserStore.set(user)
        demoModeStore.disable()
        _uiState.update { it.copy(isSubmitting = false, isSignedIn = true) }
    }

    private fun showEmailTaken() {
        _uiState.update {
            it.copy(isSubmitting = false, step = SignUpStep.ACCOUNT, emailError = EMAIL_ALREADY_REGISTERED)
        }
    }

    private fun handleSignUpError(e: Throwable) {
        val code = (e as? AuthRestException)?.errorCode
        when {
            code == AuthErrorCode.EmailExists || code == AuthErrorCode.UserAlreadyExists -> showEmailTaken()
            e is AuthWeakPasswordException || code == AuthErrorCode.WeakPassword -> _uiState.update {
                it.copy(
                    isSubmitting = false,
                    step = SignUpStep.ACCOUNT,
                    passwordError = e.toUserMessage("Пароль надто простий")
                )
            }
            code == AuthErrorCode.EmailAddressInvalid -> _uiState.update {
                it.copy(isSubmitting = false, step = SignUpStep.ACCOUNT, emailError = "Перевірте правильність email")
            }
            else -> _uiState.update {
                it.copy(isSubmitting = false, errorMessage = e.toUserMessage("Не вдалося зареєструватися. Спробуйте ще раз"))
            }
        }
    }
}
