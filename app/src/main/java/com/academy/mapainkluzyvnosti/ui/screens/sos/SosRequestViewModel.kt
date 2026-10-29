package com.academy.mapainkluzyvnosti.ui.screens.sos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.local.DeviceLocation
import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import com.academy.mapainkluzyvnosti.data.model.SosProblemType
import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import com.academy.mapainkluzyvnosti.data.repository.SosRepository
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class SosRequestUiState(
    val problemType: SosProblemType = SosProblemType.OBSTACLE,
    val comment: String = "",
    val location: GeoPoint? = null,
    val locationUnavailable: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val errorMessage: String? = null,
    val showDemoGate: Boolean = false
)

/** Без останньої відомої позиції чекаємо на перший фікс не довше за це. */
private const val FIRST_FIX_TIMEOUT_MS = 5_000L

/** Скільки фоново чекати точний GPS-фікс, щоб уточнити позицію (і вже надісланий запит). */
private const val PRECISE_FIX_TIMEOUT_MS = 20_000L

/**
 * SOS не чекає на точний GPS: кнопка активна одразу з останньою відомою позицією, а свіжий фікс
 * шукається паралельно й оновлює позицію — до відправки в формі, після відправки в самому запиті.
 *
 * [backgroundScope] живе довше за екран: після «Надіслати» екран закривається, а уточнення координат
 * запиту має завершитися.
 */
class SosRequestViewModel(
    private val sosRepository: SosRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore,
    private val appContext: Context,
    private val backgroundScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(SosRequestUiState())
    val uiState: StateFlow<SosRequestUiState> = _uiState.asStateFlow()

    private var preciseFix: Deferred<GeoPoint?>? = null

    /** Викликати, коли дозвіл на геолокацію є. */
    fun startLocating() {
        if (preciseFix != null) return
        val precise = backgroundScope.async { DeviceLocation.freshFix(appContext, PRECISE_FIX_TIMEOUT_MS) }
        preciseFix = precise

        val lastKnown = DeviceLocation.lastKnown(appContext)
        if (lastKnown != null) _uiState.update { it.copy(location = lastKnown, locationUnavailable = false) }

        viewModelScope.launch {
            if (lastKnown == null && withTimeoutOrNull(FIRST_FIX_TIMEOUT_MS) { precise.await() } == null) {
                _uiState.update { it.copy(locationUnavailable = true) }
            }
            precise.await()?.let { point -> _uiState.update { it.copy(location = point, locationUnavailable = false) } }
        }
    }

    fun onLocationPermissionDenied() {
        _uiState.update { it.copy(locationUnavailable = true) }
    }

    fun onProblemTypeChange(type: SosProblemType) {
        _uiState.update { it.copy(problemType = type) }
    }

    fun onCommentChange(text: String) {
        _uiState.update { it.copy(comment = text) }
    }

    fun dismissDemoGate() {
        _uiState.update { it.copy(showDemoGate = false) }
    }

    fun submit() {
        val location = _uiState.value.location ?: return
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
                    lat = location.lat,
                    lng = location.lng,
                    problemType = _uiState.value.problemType,
                    comment = _uiState.value.comment.trim().ifBlank { null }
                )
            }.onSuccess { requestId ->
                refineLocationAfterSubmit(requestId, sent = location)
                _uiState.update { it.copy(isSubmitting = false, isSubmitted = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = e.message ?: "Не вдалося надіслати запит") }
            }
        }
    }

    /** Запит уже пішов з останньою відомою позицією; коли прийде точний фікс — оновлюємо координати запиту. */
    private fun refineLocationAfterSubmit(requestId: String, sent: GeoPoint) {
        val precise = preciseFix ?: return
        backgroundScope.launch {
            val point = precise.await() ?: return@launch
            if (point == sent) return@launch
            runCatching { sosRepository.updateLocation(requestId, point.lat, point.lng) }
                .onFailure { e -> SupabaseErrorLog.log("PATCH /rest/v1/sos_requests (precise location)", e, mapOf("id" to requestId)) }
        }
    }

    override fun onCleared() {
        // Закрили без відправки — GPS більше не потрібен.
        if (!_uiState.value.isSubmitted) preciseFix?.cancel()
    }
}
