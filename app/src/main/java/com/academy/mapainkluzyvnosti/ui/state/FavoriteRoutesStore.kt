package com.academy.mapainkluzyvnosti.ui.state

import android.content.Context
import com.academy.mapainkluzyvnosti.data.local.SavedRoutesStorage
import com.academy.mapainkluzyvnosti.data.local.SettingsPreferences
import com.academy.mapainkluzyvnosti.data.model.Route
import com.academy.mapainkluzyvnosti.work.RouteCheckScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Обрані маршрути зберігаються на диску (SharedPreferences, [SavedRoutesStorage]),
 * а не лише в пам'яті сесії — ранкова фонова перевірка (WorkManager) читає їх
 * незалежно від того, чи запущений UI-процес застосунку. Кожна зміна списку
 * також вмикає/вимикає щоденне планування: задача не повинна тримати пристрій
 * зайнятим, якщо збережених маршрутів немає.
 */
class FavoriteRoutesStore(private val context: Context) {

    private val _routes = MutableStateFlow(SavedRoutesStorage.load(context))
    val routes: StateFlow<List<Route>> = _routes.asStateFlow()

    fun isFavorite(routeId: String): Boolean = _routes.value.any { it.id == routeId }

    fun toggle(route: Route) {
        _routes.update { current ->
            val updated = if (current.any { it.id == route.id }) {
                current.filterNot { it.id == route.id }
            } else {
                current + route
            }
            SavedRoutesStorage.save(context, updated)
            val routeNotificationsEnabled = SettingsPreferences.load(context).routeNotificationsEnabled
            if (updated.isEmpty() || !routeNotificationsEnabled) {
                RouteCheckScheduler.cancel(context)
            } else {
                RouteCheckScheduler.scheduleDaily(context)
            }
            updated
        }
    }
}
