package com.academy.mapainkluzyvnosti.ui.screens.photoupload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.remote.dto.PlacePhotoDto
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.PhotoUploadResult
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
    /** Фото збережено, але ШІ-аналіз не вдався — показуємо це, а не порожній результат. */
    val analysisFailed: Boolean = false,
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
            _uiState.update { it.copy(isUploading = true, errorMessage = null, analysisFailed = false) }
            runCatching {
                placeRepository.uploadPlacePhoto(placeId, userId, bytes, fileExtension)
            }.onSuccess { result ->
                _uiState.update {
                    when (result) {
                        is PhotoUploadResult.Analyzed -> it.copy(isUploading = false, result = result.photo)
                        is PhotoUploadResult.AnalysisFailed ->
                            it.copy(isUploading = false, result = result.photo, analysisFailed = true)
                        is PhotoUploadResult.Rejected -> it.copy(
                            isUploading = false,
                            errorMessage = "Фото не прийнято: ${result.reason}. Сфотографуйте саме це місце, " +
                                "без мемів, скриншотів і картинок з інтернету"
                        )
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isUploading = false, errorMessage = e.toUserMessage("Не вдалося завантажити фото. Спробуйте ще раз")) }
            }
        }
    }
}
