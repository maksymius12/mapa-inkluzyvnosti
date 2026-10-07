package com.academy.mapainkluzyvnosti.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Темна тема — основна за задумом дизайну; перемикач у профілі дозволяє світлу. */
class ThemeStore {

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggle() {
        _isDarkTheme.value = !_isDarkTheme.value
    }
}
