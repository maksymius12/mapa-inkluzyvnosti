package com.academy.mapainkluzyvnosti.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/** Довша сторона фото після зменшення: досить для перевірки входу, а файл лишається ~0,3–0,6 МБ. */
private const val MAX_PHOTO_SIDE = 1600
private const val JPEG_QUALITY = 85

/** Вибір фото з галереї або камери — спільний для фото місця, фото-доказу рецензента й звіту «не існує». */
class PhotoPicker internal constructor(
    private val gallery: () -> Unit,
    private val camera: () -> Unit
) {
    fun pickFromGallery() = gallery()
    fun takePhoto() = camera()
}

/**
 * Камера знімає повнорозмірне фото в тимчасовий файл (TakePicture + FileProvider), а не мініатюру
 * (TakePicturePreview). І з камери, і з галереї фото зменшується до [MAX_PHOTO_SIDE] по довшій стороні
 * з урахуванням повороту з EXIF.
 */
@Composable
fun rememberPhotoPicker(onPhoto: (Bitmap) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    // Шлях до тимчасового файлу переживає перезапуск активності, поки відкрита камера.
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun deliver(uri: Uri, deleteAfter: File?) {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { decodeScaled(context, uri) }.getOrNull().also { deleteAfter?.delete() }
            }
            bitmap?.let { currentOnPhoto(it) }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) deliver(uri, deleteAfter = null)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingCameraPath?.let(::File)
        pendingCameraPath = null
        if (file == null) return@rememberLauncherForActivityResult
        if (saved && file.length() > 0) deliver(Uri.fromFile(file), deleteAfter = file) else file.delete()
    }

    fun launchCamera() {
        val file = newCameraFile(context)
        pendingCameraPath = file.absolutePath
        cameraLauncher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }

    return PhotoPicker(
        gallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        camera = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    )
}

private fun newCameraFile(context: Context): File {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    // Старі знімки, що лишились після скасування чи падіння, прибираємо.
    dir.listFiles()?.forEach { it.delete() }
    return File(dir, "photo_${System.currentTimeMillis()}.jpg")
}

/** Декодує зображення зі зменшенням до [MAX_PHOTO_SIDE] і повертає його згідно з EXIF. */
private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // Спершу грубо (степінь двійки) під час декодування, щоб не тримати в пам'яті 12-Мп знімок.
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO_SIDE) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null

    val longest = max(decoded.width, decoded.height)
    val scale = if (longest > MAX_PHOTO_SIDE) MAX_PHOTO_SIDE.toFloat() / longest else 1f
    val rotation = resolver.openInputStream(uri)?.use { stream ->
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    if (scale == 1f && rotation == 0f) return decoded

    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotation)
    }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        .also { if (it !== decoded) decoded.recycle() }
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
        compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        stream.toByteArray()
    }
}
