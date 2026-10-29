package com.academy.mapainkluzyvnosti.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Вибір фото з галереї або камери — спільний для фото місця і фото-доказу рецензента. */
class PhotoPicker internal constructor(
    private val gallery: () -> Unit,
    private val camera: () -> Unit
) {
    fun pickFromGallery() = gallery()
    fun takePhoto() = camera()
}

@Composable
fun rememberPhotoPicker(onPhoto: (Bitmap) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPhoto by rememberUpdatedState(onPhoto)

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
                bitmap?.let { currentOnPhoto(it) }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { currentOnPhoto(it) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
    }

    return PhotoPicker(
        gallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        camera = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) cameraLauncher.launch(null) else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    )
}

/** Дві кнопки «Галерея» / «Камера». */
@Composable
fun PhotoSourceButtons(onGallery: () -> Unit, onCamera: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
            Text(" Галерея", modifier = Modifier.padding(start = 4.dp))
        }
        Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = onCamera, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null)
            Text(" Камера", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

suspend fun Bitmap.toJpegBytes(): ByteArray = withContext(Dispatchers.Default) {
    ByteArrayOutputStream().use { stream ->
        compress(Bitmap.CompressFormat.JPEG, 85, stream)
        stream.toByteArray()
    }
}
