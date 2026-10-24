package com.academy.mapainkluzyvnosti.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Стан інтернету для всього застосунку (Koin singleton). Мережа вважається доступною лише з
 * NET_CAPABILITY_INTERNET і NET_CAPABILITY_VALIDATED (система перевірила, що інтернет справді є).
 * Стежить через registerDefaultNetworkCallback — без опитування.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(currentlyOnline())
    /** true — є перевірений інтернет. */
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _reconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Подія «зв'язок відновився» — екрани перезавантажують свої дані. */
    val reconnected: SharedFlow<Unit> = _reconnected.asSharedFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            update(capabilities.isValidatedInternet())
        }

        override fun onLost(network: Network) {
            // Типова мережа зникла; якщо система вже має іншу перевірену — вона прийде наступним колбеком.
            update(currentlyOnline())
        }

        override fun onUnavailable() {
            update(false)
        }
    }

    init {
        runCatching { connectivityManager?.registerDefaultNetworkCallback(callback) }
    }

    /** Негайна перевірка (кнопка «Спробувати ще раз»). */
    fun checkNow(): Boolean {
        val online = currentlyOnline()
        update(online)
        return online
    }

    private fun update(online: Boolean) {
        val wasOnline = _isOnline.value
        _isOnline.value = online
        if (online && !wasOnline) _reconnected.tryEmit(Unit)
    }

    private fun currentlyOnline(): Boolean {
        val cm = connectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(network)?.isValidatedInternet() == true
    }

    private fun NetworkCapabilities.isValidatedInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
