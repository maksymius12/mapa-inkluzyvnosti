package com.academy.mapainkluzyvnosti.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.PlaceRequestStatus
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.ExistenceReport
import com.academy.mapainkluzyvnosti.data.repository.ExistenceReportRepository
import com.academy.mapainkluzyvnosti.ui.state.ModerationCountsStore
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminReportsUiState(
    /** null — усі звіти; за замовчуванням — ті, що очікують розгляду. */
    val filter: PlaceRequestStatus? = PlaceRequestStatus.PENDING,
    val reports: List<ExistenceReport> = emptyList(),
    val isLoading: Boolean = true,
    val isActing: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null
)

/** «Видалені місця» для адміна: повідомлення «Цього місця не існує» — прибрати місце з мапи, залишити, видалити запис. */
class AdminReportsViewModel(
    private val repository: ExistenceReportRepository,
    private val moderationCountsStore: ModerationCountsStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminReportsUiState())
    val uiState: StateFlow<AdminReportsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun setFilter(filter: PlaceRequestStatus?) {
        if (filter == _uiState.value.filter) return
        _uiState.update { it.copy(filter = filter) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.adminList(_uiState.value.filter) }
                .onSuccess { list -> _uiState.update { it.copy(isLoading = false, reports = list) } }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.toReportsMessage("Не вдалося завантажити видалені місця")) }
                }
        }
    }

    fun approve(report: ExistenceReport) =
        act("Місце прибрано з мапи", "Не вдалося прибрати місце") { repository.approve(report.id) }

    fun reject(report: ExistenceReport) =
        act("Місце лишається на мапі", "Не вдалося відхилити повідомлення") { repository.reject(report.id) }

    fun delete(report: ExistenceReport) =
        act("Запис видалено", "Не вдалося видалити запис") { repository.delete(report.id) }

    private fun act(success: String, failure: String, block: suspend () -> Unit) {
        if (_uiState.value.isActing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, errorMessage = null) }
            runCatching { block() }
                .onSuccess {
                    _uiState.update { it.copy(isActing = false, message = success) }
                    moderationCountsStore.refresh()
                    load()
                }
                .onFailure { e -> _uiState.update { it.copy(isActing = false, errorMessage = e.toReportsMessage(failure)) } }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null, errorMessage = null) }

    /** PGRST202/PGRST205 — функцій чи таблиці ще немає: міграцію 20261101120000 не застосовано. */
    private fun Throwable.toReportsMessage(fallback: String): String =
        when ((this as? PostgrestRestException)?.code) {
            "PGRST202", "PGRST205" ->
                "«Видалені місця» ще не налаштовані на сервері: застосуйте міграцію 20261101120000_place_existence_reports_and_stats.sql"
            else -> toUserMessage(fallback)
        }
}
