package com.academy.mapainkluzyvnosti.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.academy.mapainkluzyvnosti.data.local.NetworkMonitor
import org.koin.compose.koinInject

/**
 * Користувач вимкнув анімації в системі (Спеціальні можливості → «Вимкнути анімацію», або
 * «Масштаб тривалості анімації» = 0). Тоді декоративні анімації (пульсація, кільця) статичні.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Викликає [action], коли зникла й відновилася мережа, — щоб екран перезавантажив свої дані. */
@Composable
fun OnReconnected(action: () -> Unit) {
    val monitor = koinInject<NetworkMonitor>()
    val currentAction by rememberUpdatedState(action)
    LaunchedEffect(monitor) {
        monitor.reconnected.collect { currentAction() }
    }
}
