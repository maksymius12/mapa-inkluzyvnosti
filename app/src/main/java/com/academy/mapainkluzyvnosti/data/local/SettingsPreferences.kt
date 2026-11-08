package com.academy.mapainkluzyvnosti.data.local

import android.content.Context
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.data.model.decodeStoredCategories

/** Сирі SharedPreferences для персоналізації — читаються один раз при старті [com.academy.mapainkluzyvnosti.ui.state.SettingsStore]. */
object SettingsPreferences {

    private const val PREFS_NAME = "app_settings"
    /** Залишок прибраної функції SOS — видаляється при старті ([clearLegacySos]). */
    private const val KEY_LEGACY_SOS_NOTIFICATIONS = "sos_notifications_enabled"
    private const val KEY_ROUTE_NOTIFICATIONS = "route_notifications_enabled"
    private const val KEY_LARGE_TEXT = "large_text_enabled"
    private const val KEY_DEFAULT_CATEGORIES = "default_categories"
    private const val KEY_DEFAULT_CATEGORIES_KNOWN = "default_categories_known"

    data class Snapshot(
        val routeNotificationsEnabled: Boolean,
        val isLargeText: Boolean,
        val defaultCategories: Set<PlaceCategory>
    )

    fun load(context: Context): Snapshot {
        val prefs = prefs(context)
        val storedCategories = prefs.getStringSet(KEY_DEFAULT_CATEGORIES, null)
        val defaultCategories = storedCategories
            ?.let { decodeStoredCategories(it, prefs.getStringSet(KEY_DEFAULT_CATEGORIES_KNOWN, null)) }
            ?: PlaceCategory.entries.toSet()
        return Snapshot(
            routeNotificationsEnabled = prefs.getBoolean(KEY_ROUTE_NOTIFICATIONS, true),
            isLargeText = prefs.getBoolean(KEY_LARGE_TEXT, false),
            defaultCategories = defaultCategories
        )
    }

    /**
     * SOS прибрано із застосунку. Після оновлення зі старої збірки на пристрої могли лишитися незавершений
     * запит (sos_pending), список уже показаних сповіщень (sos_alerts), перемикач і канал сповіщень —
     * прибираємо їх, щоб нічого не намагалося їх прочитати чи надіслати.
     */
    fun clearLegacySos(context: Context) {
        val prefs = prefs(context)
        if (prefs.contains(KEY_LEGACY_SOS_NOTIFICATIONS)) prefs.edit().remove(KEY_LEGACY_SOS_NOTIFICATIONS).apply()
        // Без getSharedPreferences(...).edit().apply(): асинхронний запис створив би файл знову вже після видалення.
        listOf("sos_pending", "sos_alerts").forEach { name ->
            if (java.io.File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").exists()) {
                context.deleteSharedPreferences(name)
            }
        }
    }

    fun saveRouteNotifications(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ROUTE_NOTIFICATIONS, enabled).apply()
    }

    fun saveLargeText(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LARGE_TEXT, enabled).apply()
    }

    fun saveDefaultCategories(context: Context, categories: Set<PlaceCategory>) {
        prefs(context).edit()
            .putStringSet(KEY_DEFAULT_CATEGORIES, categories.map { it.name }.toSet())
            .putStringSet(KEY_DEFAULT_CATEGORIES_KNOWN, PlaceCategory.entries.map { it.name }.toSet())
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
