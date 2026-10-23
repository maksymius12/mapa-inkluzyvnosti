package com.academy.mapainkluzyvnosti.ui.screens.placedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.AppUser
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.remote.dto.PlacePhotoDto
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.FavoriteRepository
import com.academy.mapainkluzyvnosti.data.repository.PlaceReportReason
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import com.academy.mapainkluzyvnosti.data.repository.UserPlaceRepository
import com.academy.mapainkluzyvnosti.ui.components.PlaceFormState
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
    val showDemoGate: Boolean = false,
    /** Відкрита форма редагування власного місця. */
    val editForm: PlaceFormState? = null,
    val isSaving: Boolean = false,
    val editError: String? = null,
    val showDeleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val showReportDialog: Boolean = false,
    val isReporting: Boolean = false,
    val reportError: String? = null,
    val hasReported: Boolean = false,
    /** Одноразове повідомлення для снекбара. */
    val message: String? = null
) {
    // У demo-режимі кнопка доступна — саму спробу додати фото перехопить діалог гейту.
    val canAddPhoto: Boolean get() = currentUser?.ageGroup == UserAgeGroup.ADULT || isDemoMode

    /** Автор може змінити або видалити власне додане місце. */
    val isOwnPlace: Boolean
        get() = place?.isUserAdded == true && currentUser != null && place.createdBy == currentUser.id

    /** Поскаржитися можна на чуже додане користувачем місце (гостю — діалог гейту). */
    val canReport: Boolean get() = place?.isUserAdded == true && !isOwnPlace
}

class PlaceDetailViewModel(
    private val placeId: String,
    private val placeRepository: PlaceRepository,
    private val favoriteRepository: FavoriteRepository,
    private val userPlaceRepository: UserPlaceRepository,
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
                if (_uiState.value.canReport && currentUserStore.user.value != null && !demoModeStore.isDemoMode.value) {
                    loadReportState()
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.toUserMessage("Не вдалося завантажити місце")) }
            }
        }
    }

    private fun loadReportState() {
        viewModelScope.launch {
            runCatching { userPlaceRepository.hasReported(placeId) }
                .onSuccess { reported -> _uiState.update { it.copy(hasReported = reported) } }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    // ---- Редагування власного місця ----

    fun startEdit() {
        val state = _uiState.value
        val place = state.place ?: return
        if (!state.isOwnPlace) return
        _uiState.update {
            it.copy(
                editForm = PlaceFormState(name = place.name, category = place.category, address = place.address),
                editError = null
            )
        }
    }

    fun onEditFormChange(form: PlaceFormState) = _uiState.update { it.copy(editForm = form) }

    fun cancelEdit() = _uiState.update { it.copy(editForm = null, editError = null, isSaving = false) }

    fun saveEdit() {
        val state = _uiState.value
        if (state.isSaving || !state.isOwnPlace) return
        val form = state.editForm?.copy(showErrors = true) ?: return
        _uiState.update { it.copy(editForm = form, editError = null) }
        if (!form.isValid) return
        val category = form.category ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching { userPlaceRepository.updatePlace(placeId, form.name, category, form.address) }
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, editForm = null, message = "Зміни збережено") }
                    load()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSaving = false, editError = e.toUserMessage("Не вдалося зберегти зміни. Спробуйте ще раз"))
                    }
                }
        }
    }

    // ---- Видалення власного місця ----

    fun requestDelete() {
        if (_uiState.value.isOwnPlace) _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun dismissDelete() = _uiState.update { it.copy(showDeleteConfirm = false) }

    fun confirmDelete() {
        val state = _uiState.value
        if (state.isDeleting || !state.isOwnPlace) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            runCatching { userPlaceRepository.deletePlace(placeId) }
                .onSuccess { _uiState.update { it.copy(isDeleting = false, showDeleteConfirm = false, isDeleted = true) } }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            showDeleteConfirm = false,
                            message = e.toUserMessage("Не вдалося видалити місце. Спробуйте ще раз")
                        )
                    }
                }
        }
    }

    // ---- Скарги на чужі місця ----

    fun requestReport() {
        val state = _uiState.value
        if (!state.canReport) return
        if (demoModeStore.isDemoMode.value || currentUserStore.user.value == null) {
            _uiState.update { it.copy(showDemoGate = true) }
            return
        }
        if (state.hasReported) {
            _uiState.update { it.copy(message = "Ви вже поскаржилися на це місце") }
            return
        }
        _uiState.update { it.copy(showReportDialog = true, reportError = null) }
    }

    fun dismissReport() = _uiState.update { it.copy(showReportDialog = false, reportError = null) }

    fun submitReport(reason: PlaceReportReason, comment: String) {
        val userId = currentUserStore.user.value?.id ?: return
        if (_uiState.value.isReporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isReporting = true, reportError = null) }
            runCatching { userPlaceRepository.reportPlace(placeId, userId, reason, comment) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isReporting = false,
                            showReportDialog = false,
                            hasReported = true,
                            message = "Дякуємо! Скаргу надіслано"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isReporting = false, reportError = e.toUserMessage("Не вдалося надіслати скаргу. Спробуйте ще раз")) }
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
