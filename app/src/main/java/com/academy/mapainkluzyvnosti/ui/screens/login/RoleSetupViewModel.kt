package com.academy.mapainkluzyvnosti.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.ReferralSource
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.data.repository.ProfileStatus
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoleSetupUiState(
    val name: String = "",
    val purposeRole: UserPurposeRole = UserPurposeRole.RESIDENT,
    val ageGroup: UserAgeGroup = UserAgeGroup.ADULT,
    val referralSource: ReferralSource? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isDone: Boolean = false,
    val needsSignIn: Boolean = false
)

/** Один раз після першого входу через Google: мета користування й вікова категорія. */
class RoleSetupViewModel(
    private val authRepository: AuthRepository,
    private val currentUserStore: CurrentUserStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoleSetupUiState())
    val uiState: StateFlow<RoleSetupUiState> = _uiState.asStateFlow()

    private var pending: ProfileStatus.NeedsRoles? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            if (authRepository.currentUserId == null) {
                _uiState.update { it.copy(isLoading = false, needsSignIn = true) }
                return@launch
            }
            runCatching { authRepository.loadProfileStatus() }
                .onSuccess { status ->
                    when (status) {
                        // Профіль уже заповнено (наприклад, на іншому пристрої) — нічого не питаємо.
                        is ProfileStatus.Ready -> {
                            currentUserStore.set(status.user)
                            _uiState.update { it.copy(isLoading = false, isDone = true) }
                        }
                        is ProfileStatus.NeedsRoles -> {
                            pending = status
                            _uiState.update { it.copy(isLoading = false, name = status.name) }
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.toUserMessage("Не вдалося завантажити профіль")) }
                }
        }
    }

    fun onPurposeRoleChange(value: UserPurposeRole) = _uiState.update { it.copy(purposeRole = value) }
    fun onAgeGroupChange(value: UserAgeGroup) = _uiState.update { it.copy(ageGroup = value) }
    fun onReferralSourceChange(value: ReferralSource) = _uiState.update { it.copy(referralSource = value) }

    fun save() {
        val status = pending ?: return load()
        val state = _uiState.value
        if (state.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                authRepository.confirmProfileRoles(status, state.purposeRole, state.ageGroup, state.referralSource)
            }
                .onSuccess { user ->
                    currentUserStore.set(user)
                    _uiState.update { it.copy(isSaving = false, isDone = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = e.toUserMessage("Не вдалося зберегти. Спробуйте ще раз")) }
                }
        }
    }
}
