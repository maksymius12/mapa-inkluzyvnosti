package com.academy.mapainkluzyvnosti.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.SosRequest
import com.academy.mapainkluzyvnosti.data.repository.SosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val requests: List<SosRequest> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

/** «Повідомлення» — відкриті запити про допомогу, на які можуть відгукнутися волонтери. */
class NotificationsViewModel(private val sosRepository: SosRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { sosRepository.getOpenRequests() }
                .onSuccess { requests ->
                    _uiState.update { it.copy(requests = requests.sortedByDescending { r -> r.createdAt }, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Не вдалося завантажити повідомлення") }
                }
        }
    }
}
