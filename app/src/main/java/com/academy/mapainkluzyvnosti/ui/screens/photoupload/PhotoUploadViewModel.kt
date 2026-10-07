package com.academy.mapainkluzyvnosti.ui.screens.photoupload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.remote.dto.PlacePhotoDto
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhotoUploadUiState(
    val isUploading: Boolean = false,
    val result: PlacePhotoDto? = null,
    val errorMessage: String? = null,
    val showDemoGate: Boolean = false
)

class PhotoUploadViewModel(
    private val placeId: String,
    private val placeRepository: PlaceRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoUploadUiState())
    val uiState: StateFlow<PhotoUploadUiState> = _uiState.asStateFlow()

    fun dismissDemoGate() {
        _uiState.update { it.copy(showDemoGate = false) }
    }

    fun uploadPhoto(bytes: ByteArray, fileExtension: String) {
        if (demoModeStore.isDemoMode.value) {
            _uiState.update { it.copy(showDemoGate = true) }
            return
        }
        val userId = currentUserStore.user.value?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, errorMessage = null) }
            runCatching {
                placeRepository.uploadPlacePhoto(placeId, userId, bytes, fileExtension)
            }.onSuccess { photo ->
                _uiState.update { it.copy(isUploading = false, result = photo) }
            }.onFailure { e ->
                _uiState.update { it.copy(isUploading = false, errorMessage = e.message ?: "Не вдалося завантажити фото") }
            }
        }
    }
}
