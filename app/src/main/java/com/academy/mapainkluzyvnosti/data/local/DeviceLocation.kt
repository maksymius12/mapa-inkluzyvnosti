package com.academy.mapainkluzyvnosti.data.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Мінімальний доступ до геопозиції пристрою через LocationManager — без залежності на Play Services. */
object DeviceLocation {

    private const val FIX_TIMEOUT_MS = 8000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun getCurrent(context: Context): GeoPoint? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        requestSingleFix(manager)?.let { return GeoPoint(it.latitude, it.longitude) }

        val lastKnown = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        return lastKnown?.let { GeoPoint(it.latitude, it.longitude) }
    }

    private suspend fun requestSingleFix(manager: LocationManager): Location? = suspendCancellableCoroutine { cont ->
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                manager.removeUpdates(this)
                if (cont.isActive) cont.resume(location)
            }
        }

        try {
            manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            manager.removeUpdates(listener)
            if (cont.isActive) cont.resume(null)
        }
        timeoutHandler.postDelayed(timeoutRunnable, FIX_TIMEOUT_MS)

        cont.invokeOnCancellation {
            manager.removeUpdates(listener)
            timeoutHandler.removeCallbacks(timeoutRunnable)
        }
    }
}
