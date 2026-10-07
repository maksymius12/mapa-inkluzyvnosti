package com.academy.mapainkluzyvnosti.ui.screens.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.SosProblemType
import com.academy.mapainkluzyvnosti.data.repository.SosRepository
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SosRequestUiState(
    val problemType: SosProblemType = SosProblemType.OBSTACLE,
    val comment: String = "",
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val errorMessage: String? = null,
    val showDemoGate: Boolean = false
)

class SosRequestViewModel(
    private val sosRepository: SosRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SosRequestUiState())
    val uiState: StateFlow<SosRequestUiState> = _uiState.asStateFlow()

    fun onProblemTypeChange(type: SosProblemType) {
        _uiState.update { it.copy(problemType = type) }
    }

    fun onCommentChange(text: String) {
        _uiState.update { it.copy(comment = text) }
    }

    fun dismissDemoGate() {
        _uiState.update { it.copy(showDemoGate = false) }
    }

    fun submit(lat: Double, lng: Double) {
        if (demoModeStore.isDemoMode.value) {
            _uiState.update { it.copy(showDemoGate = true) }
            return
        }
        val userId = currentUserStore.user.value?.id ?: run {
            _uiState.update { it.copy(errorMessage = "Потрібно увійти в акаунт") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            runCatching {
                sosRepository.createRequest(
                    userId = userId,
                    lat = lat,
                    lng = lng,
                    problemType = _uiState.value.problemType,
                    comment = _uiState.value.comment.trim().ifBlank { null }
                )
            }.onSuccess {
                _uiState.update { it.copy(isSubmitting = false, isSubmitted = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = e.message ?: "Не вдалося надіслати запит") }
            }
        }
    }
}
