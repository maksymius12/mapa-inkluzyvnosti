package com.academy.mapainkluzyvnosti.ui.state

import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.data.repository.ProfileStatus
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Тримає CurrentUserStore узгодженим із сесією Supabase протягом усього життя процесу:
 * - сесія стала дійсною (зокрема після старту без мережі, коли токен оновився пізніше) —
 *   один раз підтягуємо профіль із profiles;
 * - сесію завершено (вихід або відкликаний refresh-токен) — очищаємо поточного користувача.
 */
class SessionSync(
    private val authRepository: AuthRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var syncedUserId: String? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            authRepository.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val userId = status.session.user?.id ?: return@collect
                        if (userId == syncedUserId) return@collect
                        syncedUserId = userId
                        val profile = runCatching { authRepository.loadProfileStatus() }.getOrNull()
                        when {
                            // Мережа ще недоступна — спробуємо при наступному оновленні сесії.
                            profile == null -> syncedUserId = null
                            profile is ProfileStatus.Ready -> {
                                val current = currentUserStore.user.value
                                if (current == null || current.id == userId) currentUserStore.set(profile.user)
                            }
                            // NeedsRoles — крок вибору мети й віку покаже навігація (вхід або старт).
                            else -> Unit
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        syncedUserId = null
                        if (!demoModeStore.isDemoMode.value) currentUserStore.clear()
                    }
                    else -> Unit
                }
            }
        }
    }
}
