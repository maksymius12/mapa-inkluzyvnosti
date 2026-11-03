package com.academy.mapainkluzyvnosti.ui.state

import com.academy.mapainkluzyvnosti.data.local.NetworkMonitor
import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import com.academy.mapainkluzyvnosti.data.repository.ModerationCounts
import com.academy.mapainkluzyvnosti.data.repository.PlaceRequestRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Скільки записів чекає розгляду (бейджі «Заявки», «Видалені місця» і вкладки «Профіль»).
 * Оновлюється при відкритті застосунку, поверненні на екран і після власних дій рецензента — без realtime.
 * Мешканцю й гостю завжди нулі (сервер так само повертає 0).
 */
class ModerationCountsStore(
    private val repository: PlaceRequestRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore,
    private val networkMonitor: NetworkMonitor,
    private val scope: CoroutineScope
) {
    private val _counts = MutableStateFlow(ModerationCounts())
    val counts: StateFlow<ModerationCounts> = _counts.asStateFlow()

    private var job: Job? = null

    init {
        // Вхід, вихід, зміна ролі — лічильники одразу перераховуються.
        scope.launch { currentUserStore.user.collect { refresh() } }
        // Якщо застосунок відкрили без мережі — оновлюємо, щойно зв'язок повернувся.
        scope.launch { networkMonitor.reconnected.collect { refresh() } }
    }

    fun refresh() {
        val user = currentUserStore.user.value
        if (user == null || !user.isReviewer || demoModeStore.isDemoMode.value) {
            _counts.value = ModerationCounts()
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            runCatching { repository.moderationCounts() }
                .onSuccess { _counts.value = it }
                .onFailure { e -> SupabaseErrorLog.log("POST /rest/v1/rpc/moderation_counts", e, emptyMap()) }
        }
    }
}
