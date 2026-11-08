package com.academy.mapainkluzyvnosti.ui.screens.quickcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.remote.toSaveErrorMessage
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.data.repository.CheckRepository
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import com.academy.mapainkluzyvnosti.data.repository.ProfileStatus
import com.academy.mapainkluzyvnosti.ui.components.CriteriaKind
import com.academy.mapainkluzyvnosti.ui.components.QuickCheckState
import com.academy.mapainkluzyvnosti.ui.components.criteriaFor
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickCheckUiState(
    val form: QuickCheckState = QuickCheckState(),
    /** Поки не відомо, вхід це чи зупинка, — форму не показуємо (інакше мигнули б 7 критеріїв замість 9). */
    val isLoadingPlace: Boolean = true,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val errorMessage: String? = null,
    val showDemoGate: Boolean = false
)

/**
 * Перевірка за 7 критеріями входу (або 9 — для зупинки): може пройти будь-який авторизований користувач для будь-якого місця
 * (своє, чуже, OSM, відкриті дані). Записує лише рядок у checks; статус місця, паркування й бали учня
 * перераховує серверний тригер — права на зміну назви чи категорії місця для цього не потрібні.
 */
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

    init {
        // Набір критеріїв — за категорією місця. Повторна перевірка починається з останніх збережених
        // значень; нове місце — з «не вибрано».
        viewModelScope.launch {
            val place = runCatching { placeRepository.getPlaceById(placeId) }.getOrNull()
            val form = when {
                place == null -> QuickCheckState()
                criteriaFor(place.category).kind == CriteriaKind.STOP -> QuickCheckState.forStop(place.stopCheck?.answers)
                else -> QuickCheckState.from(place.checks)
            }
            _uiState.update { state -> state.copy(form = form.copy(comment = state.form.comment), isLoadingPlace = false) }
        }
    }

    fun onFormChange(form: QuickCheckState) {
        _uiState.update { it.copy(form = form, errorMessage = null) }
    }

    fun dismissDemoGate() {
        _uiState.update { it.copy(showDemoGate = false) }
    }

    fun submit() {
        if (demoModeStore.isDemoMode.value) {
            _uiState.update { it.copy(showDemoGate = true) }
            return
        }
        val userId = currentUserStore.user.value?.id
        if (userId == null) {
            _uiState.update { it.copy(showDemoGate = true) }
            return
        }
        if (_uiState.value.isSubmitting) return
        val form = _uiState.value.form
        if (form.unanswered.isNotEmpty()) {
            val missing = form.unanswered.joinToString { it.title }
            _uiState.update { it.copy(errorMessage = "Оцініть усі ${form.set.criteria.size} критеріїв. Не вибрано: $missing") }
            return
        }
        val comment = form.comment.ifBlank { null }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            runCatching {
                when (form.set.kind) {
                    CriteriaKind.ENTRANCE -> checkRepository.submitCheck(placeId, userId, form.toCheckResult()!!, comment)
                    CriteriaKind.STOP -> checkRepository.submitStopCheck(placeId, userId, form.toStopAnswers()!!, comment)
                }
            }.onSuccess {
                // Бали учня нараховує сервер — підтягуємо оновлений профіль, щоб «Профіль» показав їх одразу.
                if (currentUserStore.user.value?.ageGroup == UserAgeGroup.STUDENT) {
                    (runCatching { authRepository.loadProfileStatus() }.getOrNull() as? ProfileStatus.Ready)
                        ?.let { currentUserStore.set(it.user) }
                }
                _uiState.update { it.copy(isSubmitting = false, isSubmitted = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = e.toSaveErrorMessage()) }
            }
        }
    }
}
