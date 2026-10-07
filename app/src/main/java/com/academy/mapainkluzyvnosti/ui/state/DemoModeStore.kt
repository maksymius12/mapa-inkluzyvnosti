package com.academy.mapainkluzyvnosti.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Гість" — це локальний прапорець, без будь-якого звернення до Supabase Auth.
 * У demo-режимі дозволене лише читання (публічний SELECT за RLS), запис
 * (перевірки, фото, обране, SOS) заблоковано на рівні відповідних ViewModel.
 */
class DemoModeStore {

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    fun enable() {
        _isDemoMode.value = true
    }

    fun disable() {
        _isDemoMode.value = false
    }
}
