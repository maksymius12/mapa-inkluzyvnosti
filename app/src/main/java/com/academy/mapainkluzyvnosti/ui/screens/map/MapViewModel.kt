package com.academy.mapainkluzyvnosti.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.model.SosRequest
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import com.academy.mapainkluzyvnosti.data.repository.SosRepository
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.MapFilterStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val places: List<Place> = emptyList(),
    val selectedPlaceId: String? = null,
    val sosRequests: List<SosRequest> = emptyList(),
    val selectedSosId: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val selectedPlace: Place? get() = places.firstOrNull { it.id == selectedPlaceId }
    val selectedSos: SosRequest? get() = sosRequests.firstOrNull { it.id == selectedSosId }
}

class MapViewModel(
    private val placeRepository: PlaceRepository,
    private val sosRepository: SosRepository,
    private val currentUserStore: CurrentUserStore,
    private val filterStore: MapFilterStore
) : ViewModel() {

    private val allPlaces = MutableStateFlow<List<Place>>(emptyList())
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    val isVolunteer: StateFlow<Boolean> = currentUserStore.user
        .map { it?.purposeRole == UserPurposeRole.VOLUNTEER }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        combine(allPlaces, filterStore.selection) { places, selection ->
            places.filter { it.category in selection.categories && it.status in selection.statuses }
        }.onEach { filtered ->
            _uiState.update { it.copy(places = filtered) }
        }.launchIn(viewModelScope)

        refresh()
    }

    /** Список місць і активних SOS не повинен переживати довше одного показу екрана — свіжий запит щоразу. */
    fun refresh() {
        loadPlaces()
        loadSosRequests()
    }

    fun loadPlaces() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { placeRepository.getAllPlaces() }
                .onSuccess { places ->
                    allPlaces.value = places
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message) } }
        }
    }

    fun loadSosRequests() {
        viewModelScope.launch {
            runCatching { sosRepository.getOpenRequests() }
                .onSuccess { requests -> _uiState.update { it.copy(sosRequests = requests) } }
        }
    }

    fun selectPlace(placeId: String) {
        _uiState.update { it.copy(selectedPlaceId = placeId, selectedSosId = null) }
    }

    fun selectSos(sosId: String) {
        _uiState.update { it.copy(selectedSosId = sosId, selectedPlaceId = null) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPlaceId = null, selectedSosId = null) }
    }

    fun resolveSelectedSos() {
        val sos = _uiState.value.selectedSos ?: return
        val resolverId = currentUserStore.user.value?.id ?: return
        viewModelScope.launch {
            runCatching { sosRepository.resolve(sos.id, resolverId) }
                .onSuccess {
                    _uiState.update { it.copy(selectedSosId = null) }
                    loadSosRequests()
                }
        }
    }
}
