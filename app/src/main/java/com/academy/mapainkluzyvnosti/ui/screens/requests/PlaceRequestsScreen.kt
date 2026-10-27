package com.academy.mapainkluzyvnosti.ui.screens.requests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.PlaceRequest
import com.academy.mapainkluzyvnosti.data.model.PlaceRequestStatus
import com.academy.mapainkluzyvnosti.ui.components.IconBadge
import com.academy.mapainkluzyvnosti.ui.components.MapaCard
import com.academy.mapainkluzyvnosti.ui.components.formLabel
import com.academy.mapainkluzyvnosti.ui.components.visual
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Один екран на дві ролі: рецензент (волонтер/учень/адмін) бачить чергу заявок на розгляд
 * з кнопками «Схвалити»/«Відхилити»; мешканець бачить лише свої заявки та їхній статус.
 */
@Composable
fun PlaceRequestsScreen(onBack: () -> Unit, viewModel: PlaceRequestsViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var rejectTarget by remember { mutableStateOf<PlaceRequest?>(null) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                text = if (uiState.isReviewer) "Заявки на розгляд" else "Мої заявки",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        when {
            uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.requests.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (uiState.isReviewer) "Немає заявок на розгляд" else "Ви ще не подавали заявок на нові локації",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                items(uiState.requests, key = { it.id }) { request ->
                    RequestCard(
                        request = request,
                        isReviewer = uiState.isReviewer,
                        isActing = uiState.isActing,
                        onApprove = { viewModel.approve(request.id) },
                        onReject = { rejectTarget = request },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }

    rejectTarget?.let { request ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("Відхилити заявку?") },
            text = {
                Column {
                    Text("«${request.name}» — причину можна вказати (необов'язково).")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Причина") },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.reject(request.id, reason.takeIf { it.isNotBlank() })
                        rejectTarget = null
                    }
                }) { Text("Відхилити", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { rejectTarget = null }) { Text("Скасувати") } }
        )
    }
}

@Composable
private fun RequestCard(
    request: PlaceRequest,
    isReviewer: Boolean,
    isActing: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visual = request.category.visual
    MapaCard(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(color = visual.color, icon = visual.icon, solid = false)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = request.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(
                    text = listOf(request.category.formLabel, request.address).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RequestStatusLabel(request.status, request.rejectReason)
            }
        }
        if (isReviewer && request.status == PlaceRequestStatus.PENDING) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                TextButton(onClick = onReject, enabled = !isActing) { Text("Відхилити", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onApprove, enabled = !isActing) { Text("Схвалити") }
            }
        }
    }
}

@Composable
private fun RequestStatusLabel(status: PlaceRequestStatus, rejectReason: String?) {
    val (text, color) = when (status) {
        PlaceRequestStatus.PENDING -> "Очікує розгляду" to com.academy.mapainkluzyvnosti.ui.theme.StatusUnverified
        PlaceRequestStatus.APPROVED -> "Схвалено — на мапі" to com.academy.mapainkluzyvnosti.ui.theme.StatusAccessible
        PlaceRequestStatus.REJECTED -> "Відхилено" to com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier
    }
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(top = 4.dp))
    if (status == PlaceRequestStatus.REJECTED && !rejectReason.isNullOrBlank()) {
        Text(
            text = "Причина: $rejectReason",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
