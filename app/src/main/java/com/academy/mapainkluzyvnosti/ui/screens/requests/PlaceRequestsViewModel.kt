package com.academy.mapainkluzyvnosti.ui.screens.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.PlaceRequest
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.PlaceRequestRepository
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaceRequestsUiState(
    /** Рецензент (волонтер/учень/адмін) бачить чергу на розгляд; мешканець — лише свої заявки. */
    val isReviewer: Boolean = false,
    val requests: List<PlaceRequest> = emptyList(),
    val isLoading: Boolean = true,
    val isActing: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null
)

/** Список заявок на нові локації: черга на розгляд (рецензент) або власні заявки (мешканець). */
class PlaceRequestsViewModel(
    private val repository: PlaceRequestRepository,
    private val currentUserStore: CurrentUserStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceRequestsUiState())
    val uiState: StateFlow<PlaceRequestsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        val isReviewer = currentUserStore.user.value?.isReviewer == true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isReviewer = isReviewer, errorMessage = null) }
            runCatching { if (isReviewer) repository.pendingForReview() else repository.myRequests() }
                .onSuccess { list -> _uiState.update { it.copy(isLoading = false, requests = list) } }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.toUserMessage("Не вдалося завантажити заявки"))
                    }
                }
        }
    }

    fun approve(requestId: String) {
        if (_uiState.value.isActing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, errorMessage = null) }
            runCatching { repository.approve(requestId) }
                .onSuccess {
                    _uiState.update { it.copy(isActing = false, message = "Заявку схвалено — місце на мапі") }
                    load()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isActing = false, errorMessage = e.toUserMessage("Не вдалося схвалити заявку")) }
                }
        }
    }

    fun reject(requestId: String, reason: String?) {
        if (_uiState.value.isActing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, errorMessage = null) }
            runCatching { repository.reject(requestId, reason) }
                .onSuccess {
                    _uiState.update { it.copy(isActing = false, message = "Заявку відхилено") }
                    load()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isActing = false, errorMessage = e.toUserMessage("Не вдалося відхилити заявку")) }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
