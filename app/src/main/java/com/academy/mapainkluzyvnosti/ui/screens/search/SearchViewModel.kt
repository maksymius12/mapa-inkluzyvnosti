package com.academy.mapainkluzyvnosti.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val allPlaces: List<Place> = emptyList(),
    val isLoading: Boolean = true
) {
    val results: List<Place>
        get() = if (query.isBlank()) {
            emptyList()
        } else {
            allPlaces.filter {
                it.name.contains(query, ignoreCase = true) || it.address.contains(query, ignoreCase = true)
            }
        }
}

class SearchViewModel(private val placeRepository: PlaceRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { placeRepository.getAllPlaces() }
                .onSuccess { places -> _uiState.update { it.copy(allPlaces = places, isLoading = false) } }
                .onFailure { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }
}
