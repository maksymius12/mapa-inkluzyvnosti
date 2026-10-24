package com.academy.mapainkluzyvnosti.ui.state

import android.content.Context
import com.academy.mapainkluzyvnosti.data.local.AppPreferences
import com.academy.mapainkluzyvnosti.data.model.AppUser
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.data.repository.ProfileStatus
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
        return restoreCurrentUser()
    }

    fun markOnboardingSeen() = AppPreferences.setOnboardingSeen(context)

    /** Відновлює профіль збереженої сесії; якщо після входу через Google не обрано мету й вік — крок вибору. */
    private suspend fun restoreCurrentUser(): String {
        // Сесія може бути збереженою, але ще не оновленою (немає мережі) — беремо користувача з неї.
        val storedUser = authRepository.storedSessionUser() ?: return Routes.LOGIN
        val userId = storedUser.id
        val displayName = authRepository.suggestedDisplayName()
            ?: storedUser.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: DEFAULT_NAME
        when (val status = runCatching { authRepository.loadProfileStatus() }.getOrNull()) {
            is ProfileStatus.Ready -> currentUserStore.set(status.user)
            is ProfileStatus.NeedsRoles -> return Routes.ROLE_SETUP
            // Без мережі профіль недоступний — тримаємо мінімального користувача, щоб екрани не вважали його гостем;
            // справжній профіль підтягне SessionSync, щойно сесія оновиться.
            null -> currentUserStore.set(
                AppUser(
                    id = userId,
                    name = displayName,
                    purposeRole = UserPurposeRole.RESIDENT,
                    ageGroup = UserAgeGroup.ADULT,
                    email = storedUser.email
                )
            )
        }
        return Routes.MAP
    }

    private companion object {
        const val DEFAULT_NAME = "Користувач"
    }
}
