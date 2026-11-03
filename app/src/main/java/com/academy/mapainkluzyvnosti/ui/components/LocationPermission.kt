package com.academy.mapainkluzyvnosti.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * Запит дозволу на геолокацію: точну й приблизну разом. На Android 12+ запит лише ACCESS_FINE_LOCATION
 * система ігнорує, а так людина може обрати й «приблизно» — тоді працює мережеве визначення.
 * [onResult] — true, якщо надано хоч один із двох.
 */
@Composable
fun rememberLocationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        onResult(result.values.any { it })
    }
    return {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}
