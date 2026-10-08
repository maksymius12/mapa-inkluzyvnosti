package com.academy.mapainkluzyvnosti.ui.screens.profile

import androidx.lifecycle.ViewModel
import com.academy.mapainkluzyvnosti.data.model.AppUser
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import kotlinx.coroutines.flow.StateFlow

private const val DEFAULT_NAME = "Користувач"

class ProfileViewModel(
    private val authRepository: AuthRepository,
    currentUserStore: CurrentUserStore,
    demoModeStore: DemoModeStore
) : ViewModel() {

    val currentUser: StateFlow<AppUser?> = currentUserStore.user
    val isDemoMode: StateFlow<Boolean> = demoModeStore.isDemoMode

    /** Ім'я з реального профілю; для профілів із типовою назвою — ім'я акаунта (Google/пошта). */
    fun displayName(user: AppUser?): String {
        val profileName = user?.name?.takeIf { it.isNotBlank() && it != DEFAULT_NAME }
        return profileName ?: authRepository.suggestedDisplayName() ?: DEFAULT_NAME
    }

    fun avatarUrl(): String? = authRepository.currentAvatarUrl()
}
