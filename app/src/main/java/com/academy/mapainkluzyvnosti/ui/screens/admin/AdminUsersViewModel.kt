package com.academy.mapainkluzyvnosti.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.data.model.UserRole
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.AdminRepository
import com.academy.mapainkluzyvnosti.data.repository.AdminUser
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUsersUiState(
    val isAdmin: Boolean = false,
    val currentUserId: String? = null,
    val search: String = "",
    val users: List<AdminUser> = emptyList(),
    val isLoading: Boolean = true,
    val isActing: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null
)

/** Адмін-панель: пошук користувачів, зміна ролі (користувач/адмін) і мети користування. */
class AdminUsersViewModel(
    private val repository: AdminRepository,
    currentUserStore: CurrentUserStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AdminUsersUiState(
            isAdmin = currentUserStore.user.value?.isAdmin == true,
            currentUserId = currentUserStore.user.value?.id
        )
    )
    val uiState: StateFlow<AdminUsersUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        if (_uiState.value.isAdmin) load() else _uiState.update { it.copy(isLoading = false) }
    }

    fun onSearchChange(value: String) {
        _uiState.update { it.copy(search = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            load()
        }
    }

    fun load() {
        if (!_uiState.value.isAdmin) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.listUsers(_uiState.value.search) }
                .onSuccess { list -> _uiState.update { it.copy(isLoading = false, users = list) } }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.toAdminMessage("Не вдалося завантажити користувачів"))
                    }
                }
        }
    }

    fun setRole(user: AdminUser, role: UserRole) = act(
        success = if (role == UserRole.ADMIN) "Призначено адміністратором" else "Роль адміністратора знято",
        failure = "Не вдалося змінити роль"
    ) { repository.setRole(user.id, role) }

    fun setPurpose(user: AdminUser, purposeRole: UserPurposeRole) {
        if (user.purposeRole == purposeRole) return
        act(success = "Мету користування змінено", failure = "Не вдалося змінити мету") {
            repository.setPurpose(user.id, purposeRole)
        }
    }

    private fun act(success: String, failure: String, block: suspend () -> Unit) {
        if (_uiState.value.isActing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, errorMessage = null) }
            runCatching { block() }
                .onSuccess {
                    _uiState.update { it.copy(isActing = false, message = success) }
                    load()
                }
                .onFailure { e -> _uiState.update { it.copy(isActing = false, errorMessage = e.toAdminMessage(failure)) } }
        }
    }

    /**
     * PGRST202 — RPC admin_* ще немає в базі (міграцію 20261027160000 не застосовано). Екран бачать лише
     * адміністратори, тож прямо кажемо, що робити, замість загального «не вдалося».
     */
    private fun Throwable.toAdminMessage(fallback: String): String =
        if ((this as? PostgrestRestException)?.code == "PGRST202") {
            "Адмін-панель ще не налаштована на сервері: застосуйте міграцію 20261027160000_admin_users_panel.sql"
        } else {
            toUserMessage(fallback)
        }

    fun consumeMessage() = _uiState.update { it.copy(message = null, errorMessage = null) }
}
