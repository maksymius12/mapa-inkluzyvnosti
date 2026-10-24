package com.academy.mapainkluzyvnosti.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.AppUser
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.data.remote.OTP_EXPIRED
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.data.repository.ProfileStatus
import com.academy.mapainkluzyvnosti.ui.components.OTP_LENGTH
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

private const val RESEND_COOLDOWN_SECONDS = 60
private const val CODE_VALIDITY_MS = 60 * 60 * 1000L
private const val ATTEMPTS_BEFORE_LOCK = 5
private const val FIRST_LOCK_SECONDS = 30
private const val MAX_LOCK_SECONDS = 300
private const val DEFAULT_NAME = "Користувач"

/** Навіщо введено код: підтвердження реєстрації або скидання пароля. */
enum class OtpPurpose { SIGN_UP, PASSWORD_RESET }

enum class OtpStage { ENTER_CODE, NEW_PASSWORD }

data class OtpUiState(
    val purpose: OtpPurpose,
    val email: String,
    val stage: OtpStage = OtpStage.ENTER_CODE,
    val code: String = "",
    val isVerifying: Boolean = false,
    val codeError: String? = null,
    val isResending: Boolean = false,
    val resendCooldownSeconds: Int = 0,
    val infoMessage: String? = null,
    /** Тимчасове блокування після серії невдалих спроб. */
    val lockSeconds: Int = 0,
    // «Новий пароль» (лише для скидання)
    val newPassword: String = "",
    val newPasswordConfirm: String = "",
    val newPasswordError: String? = null,
    val newPasswordConfirmError: String? = null,
    val isSavingPassword: Boolean = false,
    val passwordSaveError: String? = null,
    /** Пароль змінено — показати повідомлення (одноразово). */
    val passwordChanged: Boolean = false,
    val destination: AuthDestination? = null
) {
    val canVerify: Boolean get() = code.length == OTP_LENGTH && !isVerifying && lockSeconds == 0
    val canResend: Boolean get() = !isResending && resendCooldownSeconds == 0
}

/**
 * Один ViewModel для обох сценаріїв із 6-значним кодом із листа:
 * підтвердження реєстрації (verifyEmailOtp SIGNUP) і скидання пароля
 * (verifyEmailOtp RECOVERY -> «Новий пароль» -> updateUser).
 */
class OtpViewModel(
    purpose: OtpPurpose,
    email: String,
    sendCodeOnStart: Boolean,
    private val authRepository: AuthRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(OtpUiState(purpose = purpose, email = email.trim()))
    val uiState: StateFlow<OtpUiState> = _uiState.asStateFlow()

    private var codeSentAtMillis = System.currentTimeMillis()
    private var failedAttempts = 0
    private var cooldownJob: Job? = null
    private var lockJob: Job? = null

    init {
        if (sendCodeOnStart) sendCode(isInitial = true) else startResendCooldown()
    }

    fun onCodeChange(code: String) = _uiState.update { it.copy(code = code, codeError = null, infoMessage = null) }

    /** Викликається, коли введено всі 6 цифр (вставка чи автозаповнення) — одразу перевіряємо. */
    fun onCodeComplete(code: String) {
        _uiState.update { it.copy(code = code) }
        verify()
    }

    fun verify() {
        val state = _uiState.value
        if (!state.canVerify) return
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifying = true, codeError = null, infoMessage = null) }
            runCatching {
                when (state.purpose) {
                    OtpPurpose.SIGN_UP -> authRepository.verifySignUpCode(state.email, state.code)
                    OtpPurpose.PASSWORD_RESET -> authRepository.verifyPasswordResetCode(state.email, state.code)
                }
            }.onSuccess {
                failedAttempts = 0
                when (state.purpose) {
                    OtpPurpose.SIGN_UP -> {
                        val destination = finishSignIn()
                        _uiState.update { it.copy(isVerifying = false, destination = destination) }
                    }
                    OtpPurpose.PASSWORD_RESET -> _uiState.update { it.copy(isVerifying = false, stage = OtpStage.NEW_PASSWORD) }
                }
            }.onFailure { e -> onVerifyFailed(e) }
        }
    }

    private fun onVerifyFailed(e: Throwable) {
        val code = (e as? AuthRestException)?.errorCode
        val isNetwork = e is HttpRequestException || e is IOException
        val isRateLimited = code == AuthErrorCode.OverRequestRateLimit || (e as? RestException)?.statusCode == 429
        val message = when {
            // Supabase не розрізняє «невірний» і «прострочений», тож орієнтуємось на час надсилання (код діє 1 год).
            code == AuthErrorCode.OtpExpired && System.currentTimeMillis() - codeSentAtMillis > CODE_VALIDITY_MS -> OTP_EXPIRED
            else -> e.toUserMessage("Не вдалося перевірити код. Спробуйте ще раз")
        }
        _uiState.update { it.copy(isVerifying = false, codeError = message, code = if (isNetwork) it.code else "") }
        when {
            isNetwork -> Unit
            isRateLimited -> startLock(MAX_LOCK_SECONDS / 5)
            else -> {
                failedAttempts++
                if (failedAttempts % ATTEMPTS_BEFORE_LOCK == 0) {
                    val round = failedAttempts / ATTEMPTS_BEFORE_LOCK - 1
                    startLock((FIRST_LOCK_SECONDS shl round.coerceAtMost(4)).coerceAtMost(MAX_LOCK_SECONDS))
                }
            }
        }
    }

    /** Захист від підбору: кнопка «Підтвердити» недоступна, поки йде відлік. */
    private fun startLock(seconds: Int) {
        lockJob?.cancel()
        lockJob = viewModelScope.launch {
            for (left in seconds downTo 0) {
                _uiState.update { it.copy(lockSeconds = left) }
                if (left > 0) delay(1000)
            }
        }
    }

    fun resend() {
        if (!_uiState.value.canResend) return
        sendCode(isInitial = false)
    }

    private fun sendCode(isInitial: Boolean) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isResending = true, codeError = null, infoMessage = null) }
            runCatching {
                when (state.purpose) {
                    OtpPurpose.SIGN_UP -> authRepository.resendSignUpCode(state.email)
                    OtpPurpose.PASSWORD_RESET -> authRepository.sendPasswordResetCode(state.email)
                }
            }.onSuccess {
                codeSentAtMillis = System.currentTimeMillis()
                _uiState.update {
                    it.copy(isResending = false, code = "", infoMessage = if (isInitial) null else "Новий код надіслано")
                }
                startResendCooldown()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isResending = false, codeError = e.toUserMessage("Не вдалося надіслати код. Спробуйте пізніше"))
                }
                // Після обмеження частоти Supabase однаково не надішле лист раніше — даємо відлік.
                if (e is RestException) startResendCooldown()
            }
        }
    }

    private fun startResendCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (seconds in RESEND_COOLDOWN_SECONDS downTo 0) {
                _uiState.update { it.copy(resendCooldownSeconds = seconds) }
                if (seconds > 0) delay(1000)
            }
        }
    }

    // ---- «Новий пароль» після коду скидання ----

    fun onNewPasswordChange(value: String) = _uiState.update { it.copy(newPassword = value, newPasswordError = null, passwordSaveError = null) }

    fun onNewPasswordConfirmChange(value: String) = _uiState.update { it.copy(newPasswordConfirm = value, newPasswordConfirmError = null) }

    fun saveNewPassword() {
        val state = _uiState.value
        if (state.isSavingPassword || state.stage != OtpStage.NEW_PASSWORD) return
        val passwordError = passwordRuleError(state.newPassword)
        val confirmError = passwordConfirmError(state.newPassword, state.newPasswordConfirm)
        if (passwordError != null || confirmError != null) {
            _uiState.update { it.copy(newPasswordError = passwordError, newPasswordConfirmError = confirmError) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingPassword = true, passwordSaveError = null) }
            runCatching { authRepository.updatePassword(state.newPassword) }
                .onSuccess {
                    val destination = finishSignIn()
                    _uiState.update { it.copy(isSavingPassword = false, passwordChanged = true, destination = destination) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSavingPassword = false, passwordSaveError = e.toUserMessage("Не вдалося змінити пароль. Спробуйте ще раз"))
                    }
                }
        }
    }

    /**
     * Вихід зі скидання пароля без збереження: код уже створив тимчасову сесію,
     * її закриваємо, щоб не лишити користувача «напівввійшовшим».
     */
    fun abandonPasswordReset(onDone: () -> Unit) {
        if (_uiState.value.stage != OtpStage.NEW_PASSWORD) return onDone()
        viewModelScope.launch {
            runCatching { authRepository.signOut() }
            currentUserStore.clear()
            onDone()
        }
    }

    fun consumePasswordChanged() = _uiState.update { it.copy(passwordChanged = false) }

    fun consumeDestination() = _uiState.update { it.copy(destination = null) }

    /** Сесія вже є: підтягуємо профіль (його створив серверний тригер із метаданих реєстрації). */
    private suspend fun finishSignIn(): AuthDestination {
        demoModeStore.disable()
        return when (val status = runCatching { authRepository.loadProfileStatus() }.getOrNull()) {
            is ProfileStatus.Ready -> {
                currentUserStore.set(status.user)
                AuthDestination.MAP
            }
            is ProfileStatus.NeedsRoles -> AuthDestination.ROLE_SETUP
            // Профіль не прочитався (мережа) — як при старті без мережі: мінімальний користувач.
            null -> {
                currentUserStore.set(
                    AppUser(
                        id = authRepository.currentUserId.orEmpty(),
                        name = authRepository.suggestedDisplayName() ?: DEFAULT_NAME,
                        purposeRole = UserPurposeRole.RESIDENT,
                        ageGroup = UserAgeGroup.ADULT
                    )
                )
                AuthDestination.MAP
            }
        }
    }
}
