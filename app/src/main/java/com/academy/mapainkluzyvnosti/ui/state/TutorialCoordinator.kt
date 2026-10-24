package com.academy.mapainkluzyvnosti.ui.state

import com.academy.mapainkluzyvnosti.data.local.TutorialPreferences
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository

/** Повний інструктаж (5 слайдів) — для акаунта; скорочений (2 слайди) — для гостя. */
enum class TutorialMode { FULL, GUEST }

/**
 * Вирішує, чи показати інструктаж: один раз після першої реєстрації/входу (або першого гостьового входу).
 * «Побачив» зберігається локально (DataStore) і для акаунта — у profiles.onboarding_done,
 * щоб не показувати повторно після перевстановлення.
 */
class TutorialCoordinator(
    private val preferences: TutorialPreferences,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore,
    private val authRepository: AuthRepository
) {
    val currentMode: TutorialMode get() = if (demoModeStore.isDemoMode.value) TutorialMode.GUEST else TutorialMode.FULL

    /** Інструктаж, який треба показати зараз, або null. */
    suspend fun pendingTutorial(): TutorialMode? {
        if (demoModeStore.isDemoMode.value) {
            return if (preferences.isTutorialSeen(guest = true)) null else TutorialMode.GUEST
        }
        val user = currentUserStore.user.value ?: return null
        if (preferences.isTutorialSeen(guest = false)) return null
        if (user.onboardingDone) {
            // Бачив на іншому пристрої або до перевстановлення.
            preferences.markTutorialSeen(guest = false)
            return null
        }
        return TutorialMode.FULL
    }

    suspend fun markSeen(mode: TutorialMode) {
        preferences.markTutorialSeen(guest = mode == TutorialMode.GUEST)
        if (mode == TutorialMode.FULL) {
            val user = currentUserStore.user.value ?: return
            authRepository.markOnboardingDone(user.id)
            currentUserStore.set(user.copy(onboardingDone = true))
        }
    }

    /** Одноразова підказка перевірити підставлені мету й вікову категорію в Налаштуваннях. */
    suspend fun shouldPromptRolesReview(): Boolean {
        if (demoModeStore.isDemoMode.value) return false
        val user = currentUserStore.user.value ?: return false
        return user.rolesNeedReview && !preferences.isRolesPromptShown(user.id)
    }

    suspend fun markRolesPromptShown() {
        val user = currentUserStore.user.value ?: return
        preferences.markRolesPromptShown(user.id)
    }
}
