package com.academy.mapainkluzyvnosti.ui.state

import android.content.Context
import com.academy.mapainkluzyvnosti.data.local.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Гість" — це локальний прапорець, без будь-якого звернення до Supabase Auth.
 * У demo-режимі дозволене лише читання (публічний SELECT за RLS), запис
 * (перевірки, фото, обране, SOS) заблоковано на рівні відповідних ViewModel.
 * Прапорець зберігається на диску, щоб гість не бачив вхід при кожному запуску.
 */
class DemoModeStore(private val context: Context) {

    private val _isDemoMode = MutableStateFlow(AppPreferences.isGuest(context))
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    fun enable() {
        AppPreferences.setGuest(context, true)
        _isDemoMode.value = true
    }

    fun disable() {
        AppPreferences.setGuest(context, false)
        _isDemoMode.value = false
    }
}
