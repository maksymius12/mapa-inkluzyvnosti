package com.academy.mapainkluzyvnosti.ui.screens.placedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.AppUser
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.remote.dto.PlacePhotoDto
import com.academy.mapainkluzyvnosti.data.repository.FavoriteRepository
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaceDetailUiState(
    val place: Place? = null,
    val latestPhoto: PlacePhotoDto? = null,
    val isLoading: Boolean = true,
    val isFavorite: Boolean = false,
    val currentUser: AppUser? = null,
    val isDemoMode: Boolean = false,
    val errorMessage: String? = null,
    val showDemoGate: Boolean = false
) {
    // У demo-режимі кнопка доступна — саму спробу додати фото перехопить діалог гейту.
    val canAddPhoto: Boolean get() = currentUser?.ageGroup == UserAgeGroup.ADULT || isDemoMode
}

class PlaceDetailViewModel(
    private val placeId: String,
    private val placeRepository: PlaceRepository,
    private val favoriteRepository: FavoriteRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceDetailUiState())
    val uiState: StateFlow<PlaceDetailUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(currentUser = currentUserStore.user.value, isDemoMode = demoModeStore.isDemoMode.value) }
        load()
    }

    fun dismissDemoGate() {
        _uiState.update { it.copy(showDemoGate = false) }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val place = placeRepository.getPlaceById(placeId)
                val photo = placeRepository.getLatestPhoto(placeId)
                val userId = currentUserStore.user.value?.id
                val isFavorite = userId != null && favoriteRepository.favoritePlaceIds(userId).contains(placeId)
                Triple(place, photo, isFavorite)
            }.onSuccess { (place, photo, isFavorite) ->
                _uiState.update {
                    it.copy(place = place, latestPhoto = photo, isFavorite = isFavorite, isLoading = false)
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun toggleFavorite() {
        if (demoModeStore.isDemoMode.value) {
            _uiState.update { it.copy(showDemoGate = true) }
            return
        }
        val userId = currentUserStore.user.value?.id ?: return
        val currentlyFavorite = _uiState.value.isFavorite
        viewModelScope.launch {
            runCatching {
                if (currentlyFavorite) {
                    favoriteRepository.removeFavorite(userId, placeId)
                } else {
                    favoriteRepository.addFavorite(userId, placeId)
                }
            }.onSuccess {
                _uiState.update { it.copy(isFavorite = !currentlyFavorite) }
            }
        }
    }
}
