package com.academy.mapainkluzyvnosti.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.AppUser
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.data.repository.Contribution
import com.academy.mapainkluzyvnosti.data.repository.ContributionRepository
import com.academy.mapainkluzyvnosti.data.repository.ModerationCounts
import com.academy.mapainkluzyvnosti.ui.state.ModerationCountsStore
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DEFAULT_NAME = "Користувач"

/** Деталі «Твого внеску»: вантажаться при першому розкритті картки. */
data class ContributionState(
    val isLoading: Boolean = false,
    val data: Contribution? = null,
    val error: String? = null
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore,
    private val moderationCountsStore: ModerationCountsStore,
    private val contributionRepository: ContributionRepository
) : ViewModel() {

    private val _contribution = MutableStateFlow(ContributionState())
    val contribution: StateFlow<ContributionState> = _contribution.asStateFlow()

    /** Розкрили картку — свіжі дані (після нової перевірки список має оновитися). */
    fun loadContribution() {
        val userId = currentUserStore.user.value?.id ?: return
        if (_contribution.value.isLoading) return
        _contribution.value = _contribution.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            _contribution.value = runCatching { contributionRepository.load(userId) }.fold(
                onSuccess = { ContributionState(data = it) },
                onFailure = { _contribution.value.copy(isLoading = false, error = "Не вдалося завантажити. Перевірте з'єднання й спробуйте ще раз.") }
            )
        }
    }

    val currentUser: StateFlow<AppUser?> = currentUserStore.user
    val isDemoMode: StateFlow<Boolean> = demoModeStore.isDemoMode
    val moderationCounts: StateFlow<ModerationCounts> = moderationCountsStore.counts

    fun refreshCounts() = moderationCountsStore.refresh()

    /** Ім'я з реального профілю; для профілів із типовою назвою — ім'я акаунта (Google/пошта). */
    fun displayName(user: AppUser?): String {
        val profileName = user?.name?.takeIf { it.isNotBlank() && it != DEFAULT_NAME }
        return profileName ?: authRepository.suggestedDisplayName() ?: DEFAULT_NAME
    }

    fun avatarUrl(): String? = authRepository.currentAvatarUrl()

    /** Пошта з профілю, а якщо його ще не завантажено — з поточної сесії. */
    fun email(user: AppUser?): String? = user?.email ?: authRepository.currentEmail

    /** Вихід: сесію прибираємо навіть без мережі; гість просто виходить із демо-режиму. */
    fun signOut(onSignedOut: () -> Unit) {
        if (demoModeStore.isDemoMode.value) {
            demoModeStore.disable()
            onSignedOut()
            return
        }
        viewModelScope.launch {
            authRepository.signOut()
            currentUserStore.clear()
            onSignedOut()
        }
    }
}
