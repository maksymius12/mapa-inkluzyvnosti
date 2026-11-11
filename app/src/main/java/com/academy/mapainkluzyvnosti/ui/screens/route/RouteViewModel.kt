package com.academy.mapainkluzyvnosti.ui.screens.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.model.Route
import com.academy.mapainkluzyvnosti.data.remote.NominatimApi
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import com.academy.mapainkluzyvnosti.domain.usecase.BuildRoute
import com.academy.mapainkluzyvnosti.domain.usecase.FindBarriersOnRoute
import com.academy.mapainkluzyvnosti.domain.usecase.parseOuterRings
import com.academy.mapainkluzyvnosti.ui.state.FavoriteRoutesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CURRENT_LOCATION_LABEL = "Моє місце"

data class RouteUiState(
    val fromQuery: String = "",
    val toQuery: String = "",
    /** Пункт призначення фіксований (прийшли з картки місця через "Як дійти") — поле "Куди" нередаговане. */
    val isDestinationLocked: Boolean = false,
    val isLocatingUser: Boolean = false,
    val locationDenied: Boolean = false,
    val isLoading: Boolean = false,
    val route: Route? = null,
    val barrierPlaces: List<Place> = emptyList(),
    val errorMessage: String? = null
)

class RouteViewModel(
    private val nominatimApi: NominatimApi,
    private val buildRoute: BuildRoute,
    private val placeRepository: PlaceRepository,
    private val favoriteRoutesStore: FavoriteRoutesStore,
    private val destinationPlaceId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouteUiState(isDestinationLocked = destinationPlaceId != null))
    val uiState: StateFlow<RouteUiState> = _uiState.asStateFlow()

    val favoriteRoutes: StateFlow<List<Route>> = favoriteRoutesStore.routes

    // Межі району — для затемнення карти поза ним. Не залежать від живих даних
    // (places/SOS), тож достатньо один раз за час життя екрана.
    private val _districtBoundary = MutableStateFlow<List<List<GeoPoint>>>(emptyList())
    val districtBoundary: StateFlow<List<List<GeoPoint>>> = _districtBoundary.asStateFlow()

    private var fixedDestination: GeoPoint? = null
    private var currentLocation: GeoPoint? = null

    init {
        viewModelScope.launch {
            runCatching {
                val district = nominatimApi.searchDistrictPolygon("Шевченківський район, Київ, Україна")
                district?.geojson?.let(::parseOuterRings).orEmpty()
            }.onSuccess { rings -> _districtBoundary.value = rings }
        }
        if (destinationPlaceId != null) {
            viewModelScope.launch {
                val place = placeRepository.getPlaceById(destinationPlaceId)
                if (place != null) {
                    fixedDestination = GeoPoint(place.lat, place.lng)
                    _uiState.update { it.copy(toQuery = place.name) }
                    maybeAutoFindRoute()
                }
            }
        }
    }

    /** Викликається з екрана одразу після успішного визначення геопозиції пристрою. */
    fun setCurrentLocation(lat: Double, lng: Double) {
        currentLocation = GeoPoint(lat, lng)
        _uiState.update { it.copy(fromQuery = CURRENT_LOCATION_LABEL, isLocatingUser = false, locationDenied = false) }
        maybeAutoFindRoute()
    }

    fun onLocatingStarted() {
        _uiState.update { it.copy(isLocatingUser = true, locationDenied = false) }
    }

    fun onLocationDenied() {
        _uiState.update { it.copy(isLocatingUser = false, locationDenied = true) }
    }

    fun toggleFavoriteRoute() {
        _uiState.value.route?.let { favoriteRoutesStore.toggle(it) }
    }

    fun onFromQueryChange(query: String) {
        // Користувач редагує "Звідки" вручну — більше не покладаємось на GPS-точку.
        currentLocation = null
        _uiState.update { it.copy(fromQuery = query) }
    }

    fun onToQueryChange(query: String) {
        _uiState.update { it.copy(toQuery = query) }
    }

    /** Маршрут будується сам, щойно відомі обидві точки — без зайвого тапу на кнопку. */
    private fun maybeAutoFindRoute() {
        if (fixedDestination != null && currentLocation != null) findRoute()
    }

    fun findRoute() {
        val state = _uiState.value
        if (state.fromQuery.isBlank() || state.toQuery.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val from = currentLocation ?: nominatimApi.searchPlace("${state.fromQuery}, Київ")
                    ?.let { GeoPoint(it.lat, it.lng) }
                    ?: error("Адресу «звідки» не знайдено")
                val to = fixedDestination ?: nominatimApi.searchPlace("${state.toQuery}, Київ")
                    ?.let { GeoPoint(it.lat, it.lng) }
                    ?: error("Адресу «куди» не знайдено")
                val route = buildRoute(
                    from = from,
                    fromName = state.fromQuery,
                    to = to,
                    toName = state.toQuery
                ) ?: error("Не вдалося побудувати маршрут")
                val places = placeRepository.getAllPlaces()
                val barrierIds = FindBarriersOnRoute(route, places)
                val barrierPlaces = places.filter { it.id in barrierIds }
                route.copy(barrierPlaceIds = barrierIds) to barrierPlaces
            }.onSuccess { (route, barrierPlaces) ->
                _uiState.update { it.copy(isLoading = false, route = route, barrierPlaces = barrierPlaces) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Помилка побудови маршруту") }
            }
        }
    }
}
