package com.academy.mapainkluzyvnosti.ui.state

import android.content.Context
import com.academy.mapainkluzyvnosti.data.local.SettingsPreferences
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.work.RouteCheckScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppSettings(
    val sosNotificationsEnabled: Boolean = true,
    val routeNotificationsEnabled: Boolean = true,
    val isLargeText: Boolean = false,
    val defaultCategories: Set<PlaceCategory> = PlaceCategory.entries.toSet()
)

/** Налаштування персоналізації — персистуються в SharedPreferences, переживають рестарт застосунку. */
class SettingsStore(private val context: Context) {

    private val _settings: MutableStateFlow<AppSettings>

    init {
        val snapshot = SettingsPreferences.load(context)
        _settings = MutableStateFlow(
            AppSettings(
                sosNotificationsEnabled = snapshot.sosNotificationsEnabled,
                routeNotificationsEnabled = snapshot.routeNotificationsEnabled,
                isLargeText = snapshot.isLargeText,
                defaultCategories = snapshot.defaultCategories
            )
        )
    }

    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setSosNotificationsEnabled(enabled: Boolean) {
        SettingsPreferences.saveSosNotifications(context, enabled)
        _settings.update { it.copy(sosNotificationsEnabled = enabled) }
    }

    fun setRouteNotificationsEnabled(enabled: Boolean) {
        SettingsPreferences.saveRouteNotifications(context, enabled)
        _settings.update { it.copy(routeNotificationsEnabled = enabled) }
        // Не варто тримати пристрій зайнятим фоновою перевіркою, якщо користувач сам вимкнув ці сповіщення.
        if (enabled) RouteCheckScheduler.scheduleDaily(context) else RouteCheckScheduler.cancel(context)
    }

    fun setLargeText(enabled: Boolean) {
        SettingsPreferences.saveLargeText(context, enabled)
        _settings.update { it.copy(isLargeText = enabled) }
    }

    fun setDefaultCategories(categories: Set<PlaceCategory>) {
        SettingsPreferences.saveDefaultCategories(context, categories)
        _settings.update { it.copy(defaultCategories = categories) }
    }
}
