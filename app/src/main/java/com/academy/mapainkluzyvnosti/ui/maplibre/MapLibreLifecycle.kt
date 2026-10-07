package com.academy.mapainkluzyvnosti.ui.maplibre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.coroutines.resume

/**
 * MapLibre-плагінний (View-based) MapView немає готової Compose-обгортки в 11.x,
 * тож життєвий цикл потрібно прокидати вручну. `Lifecycle.addObserver` синхронно
 * "доганяє" поточний стан при підписці, тож ON_CREATE/ON_START/ON_RESUME
 * приходять коректно навіть якщо composable з'явився вже після старту Activity.
 */
@Composable
fun rememberMapLibreMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return mapView
}

/** getMapAsync як suspend — MapLibreMap доставляється, щойно рушій готовий (кешовано, якщо вже готовий). */
suspend fun MapView.awaitMap(): MapLibreMap = suspendCancellableCoroutine { cont ->
    getMapAsync { map -> cont.resume(map) }
}
