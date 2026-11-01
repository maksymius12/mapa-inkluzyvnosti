package com.academy.mapainkluzyvnosti.ui.screens.placedetail

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.ui.components.PhotoSourceButtons

const val EXISTENCE_COMMENT_MIN = 5
private const val EXISTENCE_COMMENT_MAX = 1000

/**
 * «Цього місця не існує»: обов'язкове пояснення, що людина бачить на місці замість заявленого,
 * і необов'язкове фото (той самий вибір «Галерея / Камера», що й для фото місця).
 */
@Composable
fun ExistenceReportDialog(
    photo: Bitmap?,
    isSending: Boolean,
    error: String?,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var comment by rememberSaveable { mutableStateOf("") }
    val isValid = comment.trim().length >= EXISTENCE_COMMENT_MIN

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Цього місця не існує") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Опишіть, що ви бачите на цьому місці замість заявленого. Звіт розгляне адміністратор.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(EXISTENCE_COMMENT_MAX) },
                    label = { Text("Що тут насправді") },
                    enabled = !isSending,
                    minLines = 3,
                    supportingText = { Text("Обов'язково, щонайменше $EXISTENCE_COMMENT_MIN символів") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text(
                    text = "Фото (необов'язково)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (photo != null) {
                    Image(
                        bitmap = photo.asImageBitmap(),
                        contentDescription = "Фото до звіту",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    TextButton(onClick = onRemovePhoto, enabled = !isSending) { Text("Прибрати фото") }
                } else {
                    PhotoSourceButtons(
                        onGallery = onPickFromGallery,
                        onCamera = onTakePhoto,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(comment) }, enabled = isValid && !isSending) {
                Text(if (isSending) "Надсилаємо…" else "Надіслати")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text("Скасувати") }
        }
    )
}
