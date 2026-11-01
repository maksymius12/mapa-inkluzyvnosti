package com.academy.mapainkluzyvnosti.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.AdminRepository
import com.academy.mapainkluzyvnosti.data.repository.MonthlyStats
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class AdminStatsUiState(
    val month: YearMonth = YearMonth.now(),
    val stats: MonthlyStats? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    /** Наперед, у майбутнє, не гортаємо — там лише нулі. */
    val canGoForward: Boolean get() = month < YearMonth.now()
}

/** Аналітика адмін-панелі: метрики за обраний місяць (RPC admin_monthly_stats). */
class AdminStatsViewModel(private val repository: AdminRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminStatsUiState())
    val uiState: StateFlow<AdminStatsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun previousMonth() = setMonth(_uiState.value.month.minusMonths(1))

    fun nextMonth() {
        if (_uiState.value.canGoForward) setMonth(_uiState.value.month.plusMonths(1))
    }

    private fun setMonth(month: YearMonth) {
        _uiState.update { it.copy(month = month) }
        load()
    }

    fun load() {
        loadJob?.cancel()
        val month = _uiState.value.month
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.monthlyStats(month.atDay(1)) }
                .onSuccess { stats -> _uiState.update { it.copy(isLoading = false, stats = stats) } }
                .onFailure { e ->
                    val message = if ((e as? PostgrestRestException)?.code == "PGRST202") {
                        "Аналітика ще не налаштована на сервері: застосуйте міграцію 20261101120000_place_existence_reports_and_stats.sql"
                    } else {
                        e.toUserMessage("Не вдалося завантажити аналітику")
                    }
                    _uiState.update { it.copy(isLoading = false, stats = null, errorMessage = message) }
                }
        }
    }
}
