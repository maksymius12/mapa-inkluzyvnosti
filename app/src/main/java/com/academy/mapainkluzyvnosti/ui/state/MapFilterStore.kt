package com.academy.mapainkluzyvnosti.ui.state

import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FilterSelection(
    val categories: Set<PlaceCategory> = PlaceCategory.entries.toSet(),
    val statuses: Set<AccessStatus> = AccessStatus.entries.toSet()
)

/** Спільний стан фільтрів мапи — переживає навігацію між MapScreen і FiltersScreen. */
class MapFilterStore(private val settingsStore: SettingsStore) {

    private val _selection = MutableStateFlow(
        FilterSelection(categories = settingsStore.settings.value.defaultCategories)
    )
    val selection: StateFlow<FilterSelection> = _selection.asStateFlow()

    fun toggleCategory(category: PlaceCategory) {
        _selection.update {
            val categories = if (category in it.categories) it.categories - category else it.categories + category
            it.copy(categories = categories)
        }
    }

    fun toggleStatus(status: AccessStatus) {
        _selection.update {
            val statuses = if (status in it.statuses) it.statuses - status else it.statuses + status
            it.copy(statuses = statuses)
        }
    }

    fun reset() {
        _selection.update { FilterSelection(categories = settingsStore.settings.value.defaultCategories) }
    }
}
