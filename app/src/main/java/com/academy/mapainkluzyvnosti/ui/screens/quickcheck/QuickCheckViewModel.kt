package com.academy.mapainkluzyvnosti.ui.screens.quickcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.data.repository.CheckRepository
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import com.academy.mapainkluzyvnosti.domain.usecase.ComputeAccessStatus
import com.academy.mapainkluzyvnosti.ui.components.QuickCheckState
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val POINTS_PER_CHECK = 10

data class QuickCheckUiState(
    val form: QuickCheckState = QuickCheckState(),
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val errorMessage: String? = null,
    val showDemoGate: Boolean = false
)

class QuickCheckViewModel(
    private val placeId: String,
    private val checkRepository: CheckRepository,
    private val placeRepository: PlaceRepository,
    private val authRepository: AuthRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickCheckUiState())
    val uiState: StateFlow<QuickCheckUiState> = _uiState.asStateFlow()

    fun onFormChange(form: QuickCheckState) {
        _uiState.update { it.copy(form = form) }
    }

    fun dismissDemoGate() {
        _uiState.update { it.copy(showDemoGate = false) }
    }

    fun submit() {
        if (demoModeStore.isDemoMode.value) {
            _uiState.update { it.copy(showDemoGate = true) }
            return
        }
        val userId = currentUserStore.user.value?.id ?: return
        val form = _uiState.value.form
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            runCatching {
                val checkResult = form.toCheckResult()
                checkRepository.submitCheck(placeId, userId, checkResult, form.comment.ifBlank { null })

                val computedStatus = ComputeAccessStatus(checkResult)
                val latestPhoto = placeRepository.getLatestPhoto(placeId)
                val finalStatus = ComputeAccessStatus.combineWithAiStatus(computedStatus, latestPhoto?.aiSuggestedStatus)
                placeRepository.updatePlaceStatus(placeId, finalStatus)
                placeRepository.updateAccessibleParking(placeId, checkResult.accessibleParking)

                val user = currentUserStore.user.value
                if (user?.ageGroup == UserAgeGroup.STUDENT) {
                    authRepository.incrementStudentProgress(userId, POINTS_PER_CHECK)
                }
            }.onSuccess {
                _uiState.update { it.copy(isSubmitting = false, isSubmitted = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = e.message ?: "Не вдалося зберегти перевірку") }
            }
        }
    }
}
