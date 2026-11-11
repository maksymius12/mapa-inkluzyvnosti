package com.academy.mapainkluzyvnosti.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.ChatMessage
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.ChatRepository
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ChatUiState(
    /** Гість або сесії немає: чат не читається й не пишеться (RLS — лише authenticated). */
    val isGuest: Boolean = false,
    val isAdmin: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    /** Останнє оновлення не вдалося (немає мережі тощо); старі повідомлення лишаються на екрані. */
    val loadError: String? = null,
    val draft: String = "",
    val isSending: Boolean = false,
    val deletingIds: Set<String> = emptySet(),
    /** Разове повідомлення (snackbar): не надіслано, не видалено. */
    val notice: String? = null,
    /** Збільшується після кожного надісланого повідомлення — екран прокручує донизу. */
    val sentCount: Int = 0
) {
    val draftProblem: ChatTextProblem? get() = validateChatText(draft)
    val canSend: Boolean get() = !isGuest && !isSending && draftProblem == null
}

/**
 * Спільний чат. Нові повідомлення — опитуванням кожні 5 с, поки екран видимий ([pollWhileVisible]).
 * Realtime не використовуємо: він розсилає рядки таблиці цілком (разом із user_id), а RPC — лише ім'я.
 */
class ChatViewModel(
    private val chatRepository: ChatRepository,
    currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            isGuest = demoModeStore.isDemoMode.value || currentUserStore.user.value == null,
            isAdmin = !demoModeStore.isDemoMode.value && currentUserStore.user.value?.isAdmin == true
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val refreshLock = Mutex()

    init {
        combine(currentUserStore.user, demoModeStore.isDemoMode) { user, isDemo ->
            _uiState.update { it.copy(isGuest = isDemo || user == null, isAdmin = !isDemo && user?.isAdmin == true) }
        }.launchIn(viewModelScope)
    }

    suspend fun pollWhileVisible() {
        while (currentCoroutineContext().isActive) {
            refresh()
            delay(CHAT_POLL_INTERVAL_MS)
        }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch { refresh() }
    }

    fun onDraftChange(text: String) = _uiState.update { it.copy(draft = text) }

    fun send() {
        val state = _uiState.value
        if (!state.canSend) return
        val text = normalizeChatText(state.draft)
        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            try {
                chatRepository.send(text)
                // Чернетку чистимо лише після успіху: при помилці текст не губиться.
                _uiState.update { it.copy(draft = "", isSending = false, sentCount = it.sentCount + 1) }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, notice = e.toChatMessage("Не вдалося надіслати повідомлення")) }
            }
        }
    }

    fun delete(messageId: String) {
        if (messageId in _uiState.value.deletingIds) return
        _uiState.update { it.copy(deletingIds = it.deletingIds + messageId) }
        viewModelScope.launch {
            try {
                chatRepository.markDeleted(messageId)
                _uiState.update { state -> state.copy(messages = state.messages.filterNot { it.id == messageId }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(notice = e.toChatMessage("Не вдалося видалити повідомлення")) }
            } finally {
                _uiState.update { it.copy(deletingIds = it.deletingIds - messageId) }
            }
        }
    }

    fun noticeShown() = _uiState.update { it.copy(notice = null) }

    /** Гість натиснув «Увійти»: виходимо з гостьового режиму, далі — екран входу. */
    fun leaveGuestMode() {
        if (demoModeStore.isDemoMode.value) demoModeStore.disable()
    }

    private suspend fun refresh() {
        if (_uiState.value.isGuest) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        refreshLock.withLock {
            try {
                val messages = chatRepository.latest()
                _uiState.update { it.copy(messages = messages, isLoading = false, loadError = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, loadError = e.toUserMessage("Не вдалося завантажити чат"))
                }
            }
        }
    }

    private fun Throwable.toChatMessage(fallback: String): String =
        (this as? PostgrestRestException)?.let { chatServerErrorText(it.code, it.error) } ?: toUserMessage(fallback)
}
