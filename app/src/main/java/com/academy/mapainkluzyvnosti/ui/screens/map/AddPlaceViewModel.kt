package com.academy.mapainkluzyvnosti.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.data.remote.NominatimApi
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.PlaceRequestRepository
import com.academy.mapainkluzyvnosti.data.repository.UserPlaceRepository
import com.academy.mapainkluzyvnosti.domain.usecase.isInsideAnyRing
import com.academy.mapainkluzyvnosti.ui.components.PlaceFormState
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AddPlaceStep { IDLE, PICKING_LOCATION, FORM }

data class AddPlaceUiState(
    val step: AddPlaceStep = AddPlaceStep.IDLE,
    val location: GeoPoint? = null,
    val form: PlaceFormState = PlaceFormState(),
    val isAddressLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    /** Схожі місця поруч — показується попередження «Схоже місце вже є». */
    val duplicates: List<Place> = emptyList(),
    /** Щойно створене місце — для повідомлення «Місце додано» й переходу до перевірки. */
    val createdPlace: Place? = null,
    /** Мешканець подав заявку (а не додав місце напряму) — показується окреме повідомлення. */
    val requestSubmitted: Boolean = false,
    val showDemoGate: Boolean = false,
    /** Помилка збереження — показується в самій панелі форми. */
    val submitError: String? = null,
    /** Одноразове повідомлення для снекбара (етап вибору позиції). */
    val message: String? = null
)

/** Сценарій «+ Додати місце»: вибір позиції на мапі -> форма -> перевірка дублів -> збереження. */
class AddPlaceViewModel(
    private val userPlaceRepository: UserPlaceRepository,
    private val placeRequestRepository: PlaceRequestRepository,
    private val nominatimApi: NominatimApi,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddPlaceUiState())
    val uiState: StateFlow<AddPlaceUiState> = _uiState.asStateFlow()

    private var addressJob: Job? = null

    /** Мешканці подають заявку на розгляд замість додавання місця напряму. */
    val isResidentRequest: Boolean get() = currentUserStore.user.value?.purposeRole == UserPurposeRole.RESIDENT

    /** Додавати можуть лише авторизовані: гостю (або без сесії) — діалог із пропозицією увійти. */
    fun startPicking(): Boolean {
        if (demoModeStore.isDemoMode.value || currentUserStore.user.value == null) {
            _uiState.update { it.copy(showDemoGate = true) }
            return false
        }
        _uiState.update { it.copy(step = AddPlaceStep.PICKING_LOCATION, duplicates = emptyList()) }
        return true
    }

    fun dismissDemoGate() = _uiState.update { it.copy(showDemoGate = false) }

    fun cancel() {
        addressJob?.cancel()
        _uiState.update { AddPlaceUiState(createdPlace = it.createdPlace) }
    }

    /** Повернутися з форми до вибору позиції (введені поля зберігаються). */
    fun backToPicking() = _uiState.update { it.copy(step = AddPlaceStep.PICKING_LOCATION) }

    /** [districtRings] порожній, якщо межу ще не завантажено, — тоді перевіряє лише сервер. */
    fun confirmLocation(point: GeoPoint, districtRings: List<List<GeoPoint>>) {
        if (districtRings.isNotEmpty() && !isInsideAnyRing(point, districtRings)) {
            _uiState.update { it.copy(message = "Місце за межами району") }
            return
        }
        val previous = _uiState.value.location
        _uiState.update { it.copy(step = AddPlaceStep.FORM, location = point) }
        if (previous != point) fillAddress(point)
    }

    private fun fillAddress(point: GeoPoint) {
        addressJob?.cancel()
        _uiState.update { it.copy(form = it.form.copy(address = ""), isAddressLoading = true) }
        addressJob = viewModelScope.launch {
            val address = runCatching { nominatimApi.reverseAddress(point.lat, point.lng) }.getOrNull()
            _uiState.update { state ->
                // Не перетираємо те, що користувач уже встиг ввести сам.
                val form = if (state.form.address.isBlank() && address != null) state.form.copy(address = address) else state.form
                state.copy(form = form, isAddressLoading = false)
            }
        }
    }

    fun onFormChange(form: PlaceFormState) {
        if (form.address != _uiState.value.form.address) addressJob?.cancel()
        _uiState.update { it.copy(form = form, isAddressLoading = it.isAddressLoading && form.address == it.form.address) }
    }

    fun dismissDuplicates() = _uiState.update { it.copy(duplicates = emptyList()) }

    /** [ignoreDuplicates] — користувач побачив схожі місця й усе одно додає. */
    fun submit(ignoreDuplicates: Boolean = false) {
        val state = _uiState.value
        if (state.isSubmitting) return
        val location = state.location ?: return
        val form = state.form.copy(showErrors = true)
        _uiState.update { it.copy(form = form, duplicates = emptyList(), submitError = null) }
        if (!form.isValid) return
        val category = form.category ?: return
        val authorId = currentUserStore.user.value?.id
        if (authorId == null || demoModeStore.isDemoMode.value) {
            _uiState.update { it.copy(showDemoGate = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            if (!ignoreDuplicates) {
                // Якщо перевірка дублів не вдалася (мережа), не блокуємо — сервер усе одно перевірить решту.
                val similar = runCatching {
                    userPlaceRepository.findSimilarNearby(form.name, location.lat, location.lng)
                }.getOrDefault(emptyList())
                if (similar.isNotEmpty()) {
                    _uiState.update { it.copy(isSubmitting = false, duplicates = similar) }
                    return@launch
                }
            }
            // Мешканці подають заявку на розгляд; волонтери, учні й адмін додають місце напряму.
            val isResident = currentUserStore.user.value?.purposeRole == UserPurposeRole.RESIDENT
            if (isResident) {
                runCatching {
                    placeRequestRepository.submit(
                        name = form.name,
                        address = form.address,
                        category = category,
                        lat = location.lat,
                        lng = location.lng,
                        note = null
                    )
                }.onSuccess {
                    addressJob?.cancel()
                    _uiState.value = AddPlaceUiState(requestSubmitted = true)
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(isSubmitting = false, submitError = e.toUserMessage("Не вдалося надіслати заявку. Спробуйте ще раз"))
                    }
                }
                return@launch
            }
            runCatching {
                userPlaceRepository.createPlace(
                    authorId = authorId,
                    name = form.name,
                    category = category,
                    address = form.address,
                    lat = location.lat,
                    lng = location.lng
                )
            }.onSuccess { place ->
                addressJob?.cancel()
                _uiState.value = AddPlaceUiState(createdPlace = place)
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isSubmitting = false, submitError = e.toUserMessage("Не вдалося додати місце. Спробуйте ще раз"))
                }
            }
        }
    }

    fun consumeCreatedPlace() = _uiState.update { it.copy(createdPlace = null) }
    fun consumeRequestSubmitted() = _uiState.update { it.copy(requestSubmitted = false) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
