package com.academy.mapainkluzyvnosti.data.local

import android.content.Context
import com.academy.mapainkluzyvnosti.data.model.AccessibilityType
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory

/** Прапорці запуску (екран привітання, гостьовий режим) та стан фільтрів мапи — переживають рестарт. */
object AppPreferences {

    private const val PREFS_NAME = "app_state"
    private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
    private const val KEY_GUEST = "guest_mode"
    private const val KEY_FILTER_CATEGORIES = "filter_categories"
    private const val KEY_FILTER_TYPES = "filter_types"

    fun isOnboardingSeen(context: Context): Boolean = prefs(context).getBoolean(KEY_ONBOARDING_SEEN, false)

    fun setOnboardingSeen(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_SEEN, true).apply()
    }

    fun isGuest(context: Context): Boolean = prefs(context).getBoolean(KEY_GUEST, false)

    fun setGuest(context: Context, guest: Boolean) {
        prefs(context).edit().putBoolean(KEY_GUEST, guest).apply()
    }

    /** null — фільтр ще ніколи не зберігався. */
    fun loadFilterCategories(context: Context): Set<PlaceCategory>? =
        prefs(context).getStringSet(KEY_FILTER_CATEGORIES, null)
            ?.mapNotNull { name -> runCatching { PlaceCategory.valueOf(name) }.getOrNull() }
            ?.toSet()

    fun loadFilterTypes(context: Context): Set<AccessibilityType>? =
        prefs(context).getStringSet(KEY_FILTER_TYPES, null)
            ?.mapNotNull { name -> runCatching { AccessibilityType.valueOf(name) }.getOrNull() }
            ?.toSet()

    fun saveFilters(context: Context, categories: Set<PlaceCategory>, types: Set<AccessibilityType>) {
        prefs(context).edit()
            .putStringSet(KEY_FILTER_CATEGORIES, categories.map { it.name }.toSet())
            .putStringSet(KEY_FILTER_TYPES, types.map { it.name }.toSet())
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
