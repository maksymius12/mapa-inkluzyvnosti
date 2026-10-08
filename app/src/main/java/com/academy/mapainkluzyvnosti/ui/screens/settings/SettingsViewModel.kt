package com.academy.mapainkluzyvnosti.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.AppUser
import com.academy.mapainkluzyvnosti.ui.components.CategoryGroup
import com.academy.mapainkluzyvnosti.data.repository.AuthRepository
import com.academy.mapainkluzyvnosti.ui.state.AppSettings
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import com.academy.mapainkluzyvnosti.ui.state.SettingsStore
import com.academy.mapainkluzyvnosti.ui.state.ThemeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsDialogState(
    val showCategoryPicker: Boolean = false,
    val showChangePassword: Boolean = false,
    val newPassword: String = "",
    val isChangingPassword: Boolean = false,
    val passwordChangeMessage: String? = null,
    val showDeleteAccountConfirm: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val deleteAccountError: String? = null
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val currentUserStore: CurrentUserStore,
    private val themeStore: ThemeStore,
    private val demoModeStore: DemoModeStore,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val currentUser: StateFlow<AppUser?> = currentUserStore.user
    val isDarkTheme: StateFlow<Boolean> = themeStore.isDarkTheme
    val isDemoMode: StateFlow<Boolean> = demoModeStore.isDemoMode
    val settings: StateFlow<AppSettings> = settingsStore.settings

    private val _dialogState = MutableStateFlow(SettingsDialogState())
    val dialogState: StateFlow<SettingsDialogState> = _dialogState.asStateFlow()

    fun toggleTheme() = themeStore.toggle()
    fun setSosNotifications(enabled: Boolean) = settingsStore.setSosNotificationsEnabled(enabled)
    fun setRouteNotifications(enabled: Boolean) = settingsStore.setRouteNotificationsEnabled(enabled)
    fun setLargeText(enabled: Boolean) = settingsStore.setLargeText(enabled)

    fun toggleDefaultGroup(group: CategoryGroup) {
        val current = settingsStore.settings.value.defaultCategories
        val updated = if (group.categories.all { it in current }) current - group.categories else current + group.categories
        if (updated.isNotEmpty()) settingsStore.setDefaultCategories(updated)
    }

    fun openCategoryPicker() = _dialogState.update { it.copy(showCategoryPicker = true) }
    fun dismissCategoryPicker() = _dialogState.update { it.copy(showCategoryPicker = false) }

    fun openChangePassword() = _dialogState.update { it.copy(showChangePassword = true, newPassword = "", passwordChangeMessage = null) }
    fun dismissChangePassword() = _dialogState.update { it.copy(showChangePassword = false) }
    fun onNewPasswordChange(value: String) = _dialogState.update { it.copy(newPassword = value, passwordChangeMessage = null) }

    fun submitPasswordChange() {
        val password = _dialogState.value.newPassword
        if (password.length < 6) {
            _dialogState.update { it.copy(passwordChangeMessage = "Пароль має містити щонайменше 6 символів") }
            return
        }
        viewModelScope.launch {
            _dialogState.update { it.copy(isChangingPassword = true, passwordChangeMessage = null) }
            runCatching { authRepository.updatePassword(password) }
                .onSuccess {
                    _dialogState.update {
                        it.copy(isChangingPassword = false, showChangePassword = false, passwordChangeMessage = "Пароль оновлено")
                    }
                }
                .onFailure { e ->
                    _dialogState.update { it.copy(isChangingPassword = false, passwordChangeMessage = e.message ?: "Не вдалося змінити пароль") }
                }
        }
    }

    fun openDeleteAccountConfirm() = _dialogState.update { it.copy(showDeleteAccountConfirm = true, deleteAccountError = null) }
    fun dismissDeleteAccountConfirm() = _dialogState.update { it.copy(showDeleteAccountConfirm = false) }

    fun confirmDeleteAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _dialogState.update { it.copy(isDeletingAccount = true, deleteAccountError = null) }
            runCatching { authRepository.deleteAccount() }
                .onSuccess {
                    currentUserStore.clear()
                    _dialogState.update { it.copy(isDeletingAccount = false, showDeleteAccountConfirm = false) }
                    onDeleted()
                }
                .onFailure { e ->
                    _dialogState.update { it.copy(isDeletingAccount = false, deleteAccountError = e.message ?: "Не вдалося видалити акаунт") }
                }
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        // У demo-режимі сесії в Supabase не було — достатньо скинути локальний прапорець.
        if (demoModeStore.isDemoMode.value) {
            demoModeStore.disable()
            onSignedOut()
            return
        }
        viewModelScope.launch {
            runCatching { authRepository.signOut() }
            currentUserStore.clear()
            onSignedOut()
        }
    }
}
