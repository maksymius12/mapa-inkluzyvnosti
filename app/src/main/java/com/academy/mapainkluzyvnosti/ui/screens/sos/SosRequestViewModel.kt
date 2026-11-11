package com.academy.mapainkluzyvnosti.ui.screens.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.local.LocationProvider
import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.model.SosProblemType
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import com.academy.mapainkluzyvnosti.ui.state.SosController
import com.academy.mapainkluzyvnosti.ui.state.SosState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Заклад поруч для «Уточнити, де я» з відстанню від останньої надійної позиції. */
data class NearbyPlace(val place: Place, val distanceM: Int)

data class ManualPickState(
    val isOpen: Boolean = false,
    /** Звідки шукати заклади й куди спершу поставити мапу; null — надійної позиції немає. */
    val anchor: GeoPoint? = null,
    val places: List<NearbyPlace> = emptyList(),
    val isLoadingPlaces: Boolean = false,
    val placesError: String? = null
)

/** Екран SOS — лише показ стану [SosController] і ручне уточнення місця; сам запит живе в контролері. */
class SosRequestViewModel(
    private val controller: SosController,
    private val locationProvider: LocationProvider,
    private val placeRepository: PlaceRepository
) : ViewModel() {

    val state: StateFlow<SosState> = controller.state

    private val _manualPick = MutableStateFlow(ManualPickState())
    val manualPick: StateFlow<ManualPickState> = _manualPick.asStateFlow()

    fun send() = controller.trigger()
    fun setDetails(problemType: SosProblemType?, comment: String) = controller.setDetails(problemType, comment)
    fun cancel() = controller.cancel()
    fun retry() = controller.retryNow()
    fun reset() = controller.reset()

    fun openManualPick() {
        val anchor = controller.state.value.location?.point ?: locationProvider.lastReliableNow()?.point
        _manualPick.value = ManualPickState(isOpen = true, anchor = anchor, isLoadingPlaces = anchor != null)
        if (anchor != null) loadNearby(anchor)
    }

    fun closeManualPick() {
        _manualPick.value = ManualPickState()
    }

    fun chooseManualLocation(point: GeoPoint) {
        controller.setManualLocation(point)
        closeManualPick()
    }

    private fun loadNearby(anchor: GeoPoint) {
        viewModelScope.launch {
            runCatching {
                // Спершу близько; якщо поруч нічого — ширше.
                val near = placeRepository.placesNearby(anchor.lat, anchor.lng, NEAR_RADIUS_M)
                near.ifEmpty { placeRepository.placesNearby(anchor.lat, anchor.lng, WIDE_RADIUS_M) }
            }.onSuccess { places ->
                val sorted = places
                    .map { NearbyPlace(it, distanceMeters(anchor, GeoPoint(it.lat, it.lng))) }
                    .sortedBy { it.distanceM }
                    .take(MAX_PLACES)
                _manualPick.value = _manualPick.value.copy(places = sorted, isLoadingPlaces = false)
            }.onFailure {
                _manualPick.value = _manualPick.value.copy(
                    isLoadingPlaces = false,
                    placesError = "Не вдалося завантажити заклади поруч — позначте місце на мапі"
                )
            }
        }
    }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Int {
        val result = FloatArray(1)
        android.location.Location.distanceBetween(a.lat, a.lng, b.lat, b.lng, result)
        return result[0].toInt()
    }

    private companion object {
        const val NEAR_RADIUS_M = 400.0
        const val WIDE_RADIUS_M = 1500.0
        const val MAX_PLACES = 30
    }
}
