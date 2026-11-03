package com.academy.mapainkluzyvnosti.ui.screens.news

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.academy.mapainkluzyvnosti.data.repository.NewsPost
import com.academy.mapainkluzyvnosti.ui.components.MapaCard
import com.academy.mapainkluzyvnosti.ui.components.PhotoSourceButtons
import com.academy.mapainkluzyvnosti.ui.components.rememberPhotoPicker
import com.academy.mapainkluzyvnosti.ui.components.parseInstantMs
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("uk")).withZone(ZoneId.of("Europe/Kyiv"))

/** Вкладка «Новини»: оголошення від адміністрації. Мешканці й гості лише читають; чату немає. */
@Composable
fun NewsScreen(viewModel: NewsViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<NewsPost?>(null) }
    val photoPicker = rememberPhotoPicker(onPhoto = viewModel::setPhoto)

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Новини",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
            )
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                uiState.posts.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.errorMessage ?: "Новин поки немає",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.posts, key = { it.id }) { post ->
                        NewsCard(post = post, canDelete = uiState.canPublish, onDelete = { deleteTarget = post })
                    }
                }
            }
        }
        if (uiState.canPublish) {
            ExtendedFloatingActionButton(
                onClick = viewModel::openEditor,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Нова публікація") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    if (uiState.showEditor) {
        NewsEditorDialog(
            state = uiState,
            onPickFromGallery = photoPicker::pickFromGallery,
            onTakePhoto = photoPicker::takePhoto,
            onRemovePhoto = viewModel::removePhoto,
            onPublish = viewModel::publish,
            onDismiss = viewModel::dismissEditor
        )
    }

    deleteTarget?.let { post ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Видалити новину?") },
            text = { Text("«${post.title}» зникне зі стрічки для всіх.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(post)
                    deleteTarget = null
                }) { Text("Видалити", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Скасувати") } }
        )
    }
}

@Composable
private fun NewsCard(post: NewsPost, canDelete: Boolean, onDelete: () -> Unit) {
    MapaCard(modifier = Modifier.fillMaxWidth()) {
        post.photoUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(180.dp)
            )
        }
        Column(modifier = Modifier.padding(16.dp)) {
            parseInstantMs(post.createdAt)?.let {
                Text(
                    text = DateFormat.format(Instant.ofEpochMilli(it)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(text = post.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            Text(text = post.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            if (canDelete) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) { Text("Видалити", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun NewsEditorDialog(
    state: NewsUiState,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onPublish: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    val valid = title.trim().length in 3..120 && body.isNotBlank()
    AlertDialog(
        onDismissRequest = { if (!state.isPublishing) onDismiss() },
        title = { Text("Нова публікація") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    label = { Text("Заголовок") },
                    singleLine = true,
                    enabled = !state.isPublishing,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it.take(5000) },
                    label = { Text("Текст") },
                    minLines = 4,
                    enabled = !state.isPublishing,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                val photo = state.editorPhoto
                if (photo == null) {
                    Text("Фото (необов'язково)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    PhotoSourceButtons(onGallery = onPickFromGallery, onCamera = onTakePhoto, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Image(
                        bitmap = photo.asImageBitmap(),
                        contentDescription = "Фото до новини",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(160.dp).padding(top = 12.dp).clip(RoundedCornerShape(12.dp))
                    )
                    TextButton(onClick = onRemovePhoto, enabled = !state.isPublishing) { Text("Прибрати фото") }
                }
                state.editorError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPublish(title, body) }, enabled = valid && !state.isPublishing) {
                Text(if (state.isPublishing) "Публікуємо…" else "Опублікувати")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isPublishing) { Text("Скасувати") } }
    )
}
