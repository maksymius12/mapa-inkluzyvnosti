package com.academy.mapainkluzyvnosti.ui.screens.requests

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.PlaceRequest
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.NameSuggestion
import com.academy.mapainkluzyvnosti.data.repository.NameSuggestionRepository
import com.academy.mapainkluzyvnosti.data.repository.PlaceRequestRepository
import com.academy.mapainkluzyvnosti.ui.state.ModerationCountsStore
import com.academy.mapainkluzyvnosti.ui.components.toJpegBytes
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaceRequestsUiState(
    /** Рецензент (волонтер/учень/адмін) бачить чергу на розгляд; мешканець — лише свої заявки. */
    val isReviewer: Boolean = false,
    val isAdmin: Boolean = false,
    val currentUserId: String? = null,
    val requests: List<PlaceRequest> = emptyList(),
    /** Пропозиції зміни назви: рецензенту — черга, мешканцю — власні. */
    val suggestions: List<NameSuggestion> = emptyList(),
    val isLoading: Boolean = true,
    val isActing: Boolean = false,
    /** Фото-докази рецензента за id заявки — без нього «Схвалити» неактивна. */
    val proofs: Map<String, ReviewProof> = emptyMap(),
    val errorMessage: String? = null,
    val message: String? = null
)

/**
 * Фото-доказ рецензента: прев'ю, JPEG для завантаження і шлях у review-photos після завантаження
 * (щоб повторне «Схвалити» після помилки RPC не вантажило файл удруге).
 */
class ReviewProof(val preview: Bitmap, val jpeg: ByteArray, val uploadedPath: String? = null)

/** Список заявок на нові локації: черга на розгляд (рецензент) або власні заявки (мешканець). */
class PlaceRequestsViewModel(
    private val repository: PlaceRequestRepository,
    private val nameSuggestionRepository: NameSuggestionRepository,
    private val moderationCountsStore: ModerationCountsStore,
    private val currentUserStore: CurrentUserStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceRequestsUiState())
    val uiState: StateFlow<PlaceRequestsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        val user = currentUserStore.user.value
        val isReviewer = user?.isReviewer == true
        moderationCountsStore.refresh()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isReviewer = isReviewer,
                    isAdmin = user?.isAdmin == true,
                    currentUserId = user?.id,
                    errorMessage = null
                )
            }
            runCatching {
                val requests = if (isReviewer) repository.pendingForReview() else repository.myRequests()
                val suggestions = when {
                    isReviewer -> nameSuggestionRepository.pending()
                    user != null -> nameSuggestionRepository.mine(user.id)
                    else -> emptyList()
                }
                requests to suggestions
            }
                .onSuccess { (requests, suggestions) ->
                    _uiState.update { it.copy(isLoading = false, requests = requests, suggestions = suggestions) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.toUserMessage("Не вдалося завантажити заявки"))
                    }
                }
        }
    }

    fun setProof(requestId: String, photo: Bitmap) {
        viewModelScope.launch {
            val jpeg = photo.toJpegBytes()
            _uiState.update { it.copy(proofs = it.proofs + (requestId to ReviewProof(photo, jpeg))) }
        }
    }

    fun removeProof(requestId: String) = _uiState.update { it.copy(proofs = it.proofs - requestId) }

    fun approve(requestId: String) {
        if (_uiState.value.isActing) return
        val proof = _uiState.value.proofs[requestId] ?: run {
            _uiState.update { it.copy(errorMessage = "Додайте фото-доказ, щоб схвалити заявку") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, errorMessage = null) }
            runCatching {
                val path = proof.uploadedPath ?: repository.uploadReviewPhoto(requestId, proof.jpeg, "jpg").also { path ->
                    _uiState.update { state ->
                        state.copy(proofs = state.proofs + (requestId to ReviewProof(proof.preview, proof.jpeg, path)))
                    }
                }
                repository.approve(requestId, path)
            }
                .onSuccess {
                    _uiState.update {
                        it.copy(isActing = false, proofs = it.proofs - requestId, message = "Заявку схвалено — місце на мапі")
                    }
                    load()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isActing = false, errorMessage = e.toUserMessage("Не вдалося схвалити заявку")) }
                }
        }
    }

    fun reject(requestId: String, reason: String?) {
        if (_uiState.value.isActing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, errorMessage = null) }
            runCatching { repository.reject(requestId, reason) }
                .onSuccess {
                    _uiState.update { it.copy(isActing = false, message = "Заявку відхилено") }
                    load()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isActing = false, errorMessage = e.toUserMessage("Не вдалося відхилити заявку")) }
                }
        }
    }

    fun approveSuggestion(id: String) = decideSuggestion(id, approve = true)

    fun rejectSuggestion(id: String) = decideSuggestion(id, approve = false)

    private fun decideSuggestion(id: String, approve: Boolean) {
        if (_uiState.value.isActing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, errorMessage = null) }
            runCatching {
                if (approve) nameSuggestionRepository.approve(id) else nameSuggestionRepository.reject(id)
            }
                .onSuccess {
                    _uiState.update {
                        it.copy(isActing = false, message = if (approve) "Назву змінено" else "Пропозицію відхилено")
                    }
                    load()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isActing = false, errorMessage = e.toUserMessage("Не вдалося зберегти рішення. Спробуйте ще раз"))
                    }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
