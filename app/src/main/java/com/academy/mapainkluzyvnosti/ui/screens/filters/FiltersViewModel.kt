package com.academy.mapainkluzyvnosti.ui.screens.filters

import androidx.lifecycle.ViewModel
import com.academy.mapainkluzyvnosti.data.model.AccessibilityType
import com.academy.mapainkluzyvnosti.ui.components.CategoryGroup
import com.academy.mapainkluzyvnosti.ui.state.FilterSelection
import com.academy.mapainkluzyvnosti.ui.state.MapFilterStore
import kotlinx.coroutines.flow.StateFlow

class FiltersViewModel(private val filterStore: MapFilterStore) : ViewModel() {

    val selection: StateFlow<FilterSelection> = filterStore.selection

    fun toggleType(type: AccessibilityType) = filterStore.toggleType(type)
    fun toggleCategoryGroup(group: CategoryGroup) = filterStore.toggleCategoryGroup(group)
    fun showOnlyCategoryGroup(group: CategoryGroup) = filterStore.showOnlyCategoryGroup(group)
    fun showAllCategories() = filterStore.showAllCategories()
    fun reset() = filterStore.reset()
}
