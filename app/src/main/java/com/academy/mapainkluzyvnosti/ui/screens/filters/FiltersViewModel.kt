package com.academy.mapainkluzyvnosti.ui.screens.filters

import androidx.lifecycle.ViewModel
import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.ui.state.FilterSelection
import com.academy.mapainkluzyvnosti.ui.state.MapFilterStore
import kotlinx.coroutines.flow.StateFlow

class FiltersViewModel(private val filterStore: MapFilterStore) : ViewModel() {

    val selection: StateFlow<FilterSelection> = filterStore.selection

    fun toggleCategory(category: PlaceCategory) = filterStore.toggleCategory(category)
    fun toggleStatus(status: AccessStatus) = filterStore.toggleStatus(status)
    fun reset() = filterStore.reset()
}
