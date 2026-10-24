package com.academy.mapainkluzyvnosti.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.AppUser
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val DEFAULT_NAME = "Користувач"

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) : ViewModel() {

    val currentUser: StateFlow<AppUser?> = currentUserStore.user
    val isDemoMode: StateFlow<Boolean> = demoModeStore.isDemoMode

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
