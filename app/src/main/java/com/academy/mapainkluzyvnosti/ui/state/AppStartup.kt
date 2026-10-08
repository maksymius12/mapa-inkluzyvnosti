package com.academy.mapainkluzyvnosti.ui.state

import android.content.Context
import com.academy.mapainkluzyvnosti.data.local.AppPreferences
import com.academy.mapainkluzyvnosti.data.model.AppUser
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.ui.navigation.Routes

/**
 * Вирішує, з якого екрана стартує застосунок: привітання — лише при першому запуску,
 * далі одразу мапа (для гостя або користувача зі збереженою сесією), інакше — вхід.
 */
class AppStartup(
    private val context: Context,
    private val authRepository: AuthRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) {

    suspend fun resolveStartRoute(): String {
        if (!AppPreferences.isOnboardingSeen(context)) return Routes.WELCOME
        if (demoModeStore.isDemoMode.value) return Routes.MAP
        if (!authRepository.awaitRestoredSession()) return Routes.LOGIN
        restoreCurrentUser()
        return Routes.MAP
    }

    fun markOnboardingSeen() = AppPreferences.setOnboardingSeen(context)

    private suspend fun restoreCurrentUser() {
        val userId = authRepository.currentUserId ?: return
        val displayName = authRepository.suggestedDisplayName() ?: DEFAULT_NAME
        val profile = runCatching { authRepository.fetchOrCreateProfile(userId, displayName) }.getOrNull()
        // Без мережі профіль недоступний — тримаємо мінімального користувача, щоб екрани не вважали його гостем.
        currentUserStore.set(
            profile ?: AppUser(
                id = userId,
                name = displayName,
                purposeRole = UserPurposeRole.RESIDENT,
                ageGroup = UserAgeGroup.ADULT
            )
        )
    }

    private companion object {
        const val DEFAULT_NAME = "Користувач"
    }
}
