package com.academy.mapainkluzyvnosti.ui.state

import android.content.Context
import com.academy.mapainkluzyvnosti.data.local.AppPreferences
import com.academy.mapainkluzyvnosti.data.model.AccessibilityType
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.data.model.accessibilityTypes
import com.academy.mapainkluzyvnosti.ui.components.CategoryGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FilterSelection(
    val categories: Set<PlaceCategory> = PlaceCategory.entries.toSet(),
    val types: Set<AccessibilityType> = AccessibilityType.entries.toSet()
) {
    val allTypesSelected: Boolean get() = types.size == AccessibilityType.entries.size

    /**
     * Заклад видимий, якщо його категорія увімкнена й він має хоча б один увімкнений тип доступності.
     * Заклади без підтверджених типів (ще не перевірені) показуються лише коли фільтр типів не звужено.
     */
    fun matches(place: Place): Boolean {
        if (place.category !in categories) return false
        val placeTypes = place.accessibilityTypes
        return if (placeTypes.isEmpty()) allTypesSelected else placeTypes.any { it in types }
    }

    fun isGroupSelected(group: CategoryGroup): Boolean = group.categories.all { it in categories }
}

/** Спільний стан фільтрів мапи — переживає навігацію й перезапуск застосунку. */
class MapFilterStore(private val context: Context, private val settingsStore: SettingsStore) {

    private val _selection = MutableStateFlow(
        FilterSelection(
            categories = AppPreferences.loadFilterCategories(context) ?: settingsStore.settings.value.defaultCategories,
            types = AppPreferences.loadFilterTypes(context) ?: AccessibilityType.entries.toSet()
        )
    )
    val selection: StateFlow<FilterSelection> = _selection.asStateFlow()

    fun toggleType(type: AccessibilityType) = change {
        val types = if (type in it.types) it.types - type else it.types + type
        it.copy(types = types)
    }

    /** Чип над мапою: null — «Усі», інакше показати лише обраний тип. */
    fun selectTypeChip(type: AccessibilityType?) = change {
        it.copy(types = if (type == null) AccessibilityType.entries.toSet() else setOf(type))
    }

    fun toggleCategoryGroup(group: CategoryGroup) = change {
        val categories = if (it.isGroupSelected(group)) it.categories - group.categories else it.categories + group.categories
        it.copy(categories = categories)
    }

    fun showOnlyCategoryGroup(group: CategoryGroup) = change { it.copy(categories = group.categories) }

    fun showAllCategories() = change { it.copy(categories = PlaceCategory.entries.toSet()) }

    fun reset() = change {
        FilterSelection(categories = settingsStore.settings.value.defaultCategories)
    }

    private fun change(transform: (FilterSelection) -> FilterSelection) {
        _selection.update(transform)
        val current = _selection.value
        AppPreferences.saveFilters(context, current.categories, current.types)
    }
}
