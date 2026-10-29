package com.academy.mapainkluzyvnosti.ui.screens.photoupload

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.ui.components.DemoGateDialog
import com.academy.mapainkluzyvnosti.ui.components.PhotoSourceButtons
import com.academy.mapainkluzyvnosti.ui.components.color
import com.academy.mapainkluzyvnosti.ui.components.label
import com.academy.mapainkluzyvnosti.ui.components.rememberPhotoPicker
import com.academy.mapainkluzyvnosti.ui.components.toJpegBytes
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoUploadScreen(
    placeId: String,
    onDone: () -> Unit,
    onRequestLogin: () -> Unit,
    viewModel: PhotoUploadViewModel = koinViewModel(parameters = { parametersOf(placeId) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    if (uiState.showDemoGate) {
        DemoGateDialog(onDismiss = viewModel::dismissDemoGate, onSignInWithGoogle = onRequestLogin)
    }

    val photoPicker = rememberPhotoPicker { bitmap ->
        previewBitmap = bitmap
        scope.launch { viewModel.uploadPhoto(bitmap.toJpegBytes(), "jpg") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Додати фото") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Сфотографуйте вхід до будівлі — AI проаналізує доступність і запропонує статус.",
                style = MaterialTheme.typography.bodyMedium
            )

            previewBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            if (uiState.result == null) {
                PhotoSourceButtons(
                    onGallery = photoPicker::pickFromGallery,
                    onCamera = photoPicker::takePhoto,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            if (uiState.isUploading) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = "Аналізуємо фото…",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            uiState.result?.let { result ->
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    Text(text = "Результат аналізу", style = MaterialTheme.typography.titleMedium)
                    result.aiTitle?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                    }
                    result.aiSuggestedStatus?.let { status ->
                        Text(
                            text = "Пропозиція статусу: ${status.label()}",
                            color = status.color(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (result.aiSuggestions.isNotEmpty()) {
                        Text(
                            text = "Рекомендації:",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        result.aiSuggestions.forEach { suggestion ->
                            Text(text = "• $suggestion", modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Text(" Готово", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
