package com.academy.mapainkluzyvnosti.ui.screens.sos

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.academy.mapainkluzyvnosti.data.local.DeviceLocation
import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import com.academy.mapainkluzyvnosti.data.model.SosProblemType
import com.academy.mapainkluzyvnosti.ui.components.DemoGateDialog
import com.academy.mapainkluzyvnosti.ui.components.label
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosRequestScreen(onDone: () -> Unit, onRequestLogin: () -> Unit, viewModel: SosRequestViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showDemoGate) {
        DemoGateDialog(onDismiss = viewModel::dismissDemoGate, onSignInWithGoogle = onRequestLogin)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var location by remember { mutableStateOf<GeoPoint?>(null) }
    var locationDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch {
                location = DeviceLocation.getCurrent(context)
                locationDenied = location == null
            }
        } else {
            locationDenied = true
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            location = DeviceLocation.getCurrent(context)
            locationDenied = location == null
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(uiState.isSubmitted) {
        if (uiState.isSubmitted) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Потрібна допомога") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { innerPadding ->
        SosRequestForm(
            innerPadding = innerPadding,
            uiState = uiState,
            hasLocation = location != null,
            locationDenied = locationDenied,
            onProblemTypeChange = viewModel::onProblemTypeChange,
            onCommentChange = viewModel::onCommentChange,
            onSubmit = { location?.let { viewModel.submit(it.lat, it.lng) } }
        )
    }
}

@Composable
private fun SosRequestForm(
    innerPadding: PaddingValues,
    uiState: SosRequestUiState,
    hasLocation: Boolean,
    locationDenied: Boolean,
    onProblemTypeChange: (SosProblemType) -> Unit,
    onCommentChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Повідомлення побачать волонтери поруч. Це не виклик екстрених служб — у критичній ситуації телефонуй 101/112.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )

        Text(
            text = "Що сталося?",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
        )
        SosProblemType.entries.forEach { type ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = uiState.problemType == type, onClick = { onProblemTypeChange(type) })
                Text(text = type.label(), modifier = Modifier.padding(start = 4.dp))
            }
        }

        OutlinedTextField(
            value = uiState.comment,
            onValueChange = onCommentChange,
            label = { Text("Коментар (необов'язково)") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

        if (locationDenied) {
            Text(
                text = "Не вдалося визначити місцезнаходження. Перевір дозвіл на геолокацію та GPS.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Button(
            onClick = onSubmit,
            enabled = hasLocation && !uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(if (hasLocation) "Надіслати" else "Визначаємо місцезнаходження…")
            }
        }
    }
}
