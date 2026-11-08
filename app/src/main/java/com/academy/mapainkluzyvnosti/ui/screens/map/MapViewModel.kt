package com.academy.mapainkluzyvnosti.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.AccessibilityType
import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.remote.NominatimApi
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import com.academy.mapainkluzyvnosti.domain.usecase.parseOuterRings
import com.academy.mapainkluzyvnosti.ui.state.FilterSelection
import com.academy.mapainkluzyvnosti.ui.state.MapFilterStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val places: List<Place> = emptyList(),
    val activeTypes: Set<AccessibilityType> = AccessibilityType.entries.toSet(),
    val selectedPlaceId: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val selectedPlace: Place? get() = places.firstOrNull { it.id == selectedPlaceId }
}

class MapViewModel(
    private val placeRepository: PlaceRepository,
    private val filterStore: MapFilterStore,
    private val nominatimApi: NominatimApi
) : ViewModel() {

    private val allPlaces = MutableStateFlow<List<Place>>(emptyList())
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    val filterSelection: StateFlow<FilterSelection> = filterStore.selection

    // Межі району — для затемнення мапи поза ним і обмеження камери.
    private val _districtBoundary = MutableStateFlow<List<List<GeoPoint>>>(emptyList())
    val districtBoundary: StateFlow<List<List<GeoPoint>>> = _districtBoundary.asStateFlow()

    init {
        combine(allPlaces, filterStore.selection) { places, selection ->
            places.filter { selection.matches(it) } to selection.types
        }.onEach { (filtered, types) ->
            _uiState.update { state ->
                // Вибране місце, яке фільтр сховав, більше не показуємо в нижній панелі.
                val keepSelection = state.selectedPlaceId?.takeIf { id -> filtered.any { it.id == id } }
                state.copy(places = filtered, activeTypes = types, selectedPlaceId = keepSelection)
            }
        }.launchIn(viewModelScope)

        loadDistrictBoundary()
        refresh()
    }

    private fun loadDistrictBoundary() {
        viewModelScope.launch {
            runCatching {
                val district = nominatimApi.searchDistrictPolygon("Шевченківський район, Київ, Україна")
                district?.geojson?.let(::parseOuterRings).orEmpty()
            }.onSuccess { rings -> _districtBoundary.value = rings }
        }
    }

    /** Список місць не повинен переживати довше одного показу екрана — свіжий запит щоразу. */
    fun refresh() {
        if (_districtBoundary.value.isEmpty()) loadDistrictBoundary()
        loadPlaces()
    }

    fun loadPlaces() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { placeRepository.getAllPlaces() }
                .onSuccess { places ->
                    allPlaces.value = places
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.toUserMessage("Не вдалося завантажити місця. Спробуйте пізніше"))
                    }
                }
        }
    }

    /** Щойно додане користувачем місце — одразу на мапі й вибране, не чекаючи наступного оновлення. */
    fun onPlaceAdded(place: Place) {
        allPlaces.update { places -> places.filterNot { it.id == place.id } + place }
        _uiState.update { it.copy(selectedPlaceId = place.id) }
    }

    fun selectTypeChip(type: AccessibilityType?) = filterStore.selectTypeChip(type)

    fun selectPlace(placeId: String) {
        _uiState.update { it.copy(selectedPlaceId = placeId) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPlaceId = null) }
    }
}
