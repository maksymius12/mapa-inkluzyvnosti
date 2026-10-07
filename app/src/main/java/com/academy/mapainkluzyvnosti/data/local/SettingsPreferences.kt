package com.academy.mapainkluzyvnosti.data.local

import android.content.Context
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory

/** Сирі SharedPreferences для персоналізації — читаються один раз при старті [com.academy.mapainkluzyvnosti.ui.state.SettingsStore]. */
object SettingsPreferences {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_SOS_NOTIFICATIONS = "sos_notifications_enabled"
    private const val KEY_ROUTE_NOTIFICATIONS = "route_notifications_enabled"
    private const val KEY_LARGE_TEXT = "large_text_enabled"
    private const val KEY_DEFAULT_CATEGORIES = "default_categories"

    data class Snapshot(
        val sosNotificationsEnabled: Boolean,
        val routeNotificationsEnabled: Boolean,
        val isLargeText: Boolean,
        val defaultCategories: Set<PlaceCategory>
    )

    fun load(context: Context): Snapshot {
        val prefs = prefs(context)
        val storedCategories = prefs.getStringSet(KEY_DEFAULT_CATEGORIES, null)
        val defaultCategories = storedCategories
            ?.mapNotNull { name -> runCatching { PlaceCategory.valueOf(name) }.getOrNull() }
            ?.toSet()
            ?: PlaceCategory.entries.toSet()
        return Snapshot(
            sosNotificationsEnabled = prefs.getBoolean(KEY_SOS_NOTIFICATIONS, true),
            routeNotificationsEnabled = prefs.getBoolean(KEY_ROUTE_NOTIFICATIONS, true),
            isLargeText = prefs.getBoolean(KEY_LARGE_TEXT, false),
            defaultCategories = defaultCategories
        )
    }

    fun saveSosNotifications(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOS_NOTIFICATIONS, enabled).apply()
    }

    fun saveRouteNotifications(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ROUTE_NOTIFICATIONS, enabled).apply()
    }

    fun saveLargeText(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LARGE_TEXT, enabled).apply()
    }

    fun saveDefaultCategories(context: Context, categories: Set<PlaceCategory>) {
        prefs(context).edit().putStringSet(KEY_DEFAULT_CATEGORIES, categories.map { it.name }.toSet()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
