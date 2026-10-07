package com.academy.mapainkluzyvnosti.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.model.Route
import com.academy.mapainkluzyvnosti.data.repository.FavoriteRepository
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.FavoriteRoutesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val places: List<Place> = emptyList(),
    val routes: List<Route> = emptyList(),
    val isLoading: Boolean = true
)

class FavoritesViewModel(
    private val favoriteRepository: FavoriteRepository,
    private val placeRepository: PlaceRepository,
    favoriteRoutesStore: FavoriteRoutesStore,
    private val currentUserStore: CurrentUserStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        favoriteRoutesStore.routes.onEach { routes ->
            _uiState.update { it.copy(routes = routes) }
        }.launchIn(viewModelScope)

        loadFavoritePlaces()
    }

    fun loadFavoritePlaces() {
        val userId = currentUserStore.user.value?.id ?: run {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                val favoriteIds = favoriteRepository.favoritePlaceIds(userId).toSet()
                placeRepository.getAllPlaces().filter { it.id in favoriteIds }
            }.onSuccess { places ->
                _uiState.update { it.copy(places = places, isLoading = false) }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
