package com.academy.mapainkluzyvnosti.ui.screens.news

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.NewsPost
import com.academy.mapainkluzyvnosti.data.repository.NewsRepository
import com.academy.mapainkluzyvnosti.ui.components.toJpegBytes
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewsUiState(
    val posts: List<NewsPost> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /** Публікувати може лише адмін (як і RLS news_posts). */
    val canPublish: Boolean = false,
    val showEditor: Boolean = false,
    val editorPhoto: Bitmap? = null,
    val isPublishing: Boolean = false,
    val editorError: String? = null,
    val message: String? = null
)

class NewsViewModel(
    private val repository: NewsRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private var photoJpeg: ByteArray? = null

    init {
        // Профіль може довантажитися вже після відкриття вкладки — право публікувати стежимо постійно.
        viewModelScope.launch {
            combine(currentUserStore.user, demoModeStore.isDemoMode) { user, demo -> user?.isAdmin == true && !demo }
                .collect { canPublish -> _uiState.update { it.copy(canPublish = canPublish) } }
        }
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = it.posts.isEmpty(), errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { posts -> _uiState.update { it.copy(posts = posts, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.toUserMessage("Не вдалося завантажити новини")) } }
        }
    }

    fun openEditor() {
        if (!_uiState.value.canPublish) return
        photoJpeg = null
        _uiState.update { it.copy(showEditor = true, editorPhoto = null, editorError = null) }
    }

    fun dismissEditor() {
        photoJpeg = null
        _uiState.update { it.copy(showEditor = false, editorPhoto = null, editorError = null) }
    }

    fun setPhoto(photo: Bitmap) {
        viewModelScope.launch {
            photoJpeg = photo.toJpegBytes()
            _uiState.update { it.copy(editorPhoto = photo) }
        }
    }

    fun removePhoto() {
        photoJpeg = null
        _uiState.update { it.copy(editorPhoto = null) }
    }

    fun publish(title: String, body: String) {
        val userId = currentUserStore.user.value?.id ?: return
        if (_uiState.value.isPublishing || !_uiState.value.canPublish) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPublishing = true, editorError = null) }
            runCatching { repository.publish(userId, title, body, photoJpeg) }
                .onSuccess {
                    photoJpeg = null
                    _uiState.update {
                        it.copy(isPublishing = false, showEditor = false, editorPhoto = null, message = "Новину опубліковано")
                    }
                    load()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isPublishing = false, editorError = e.toUserMessage("Не вдалося опублікувати. Спробуйте ще раз")) }
                }
        }
    }

    fun delete(post: NewsPost) {
        if (!_uiState.value.canPublish) return
        viewModelScope.launch {
            runCatching { repository.delete(post) }
                .onSuccess {
                    _uiState.update { it.copy(message = "Новину видалено") }
                    load()
                }
                .onFailure { e -> _uiState.update { it.copy(message = e.toUserMessage("Не вдалося видалити новину")) } }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
