package com.academy.mapainkluzyvnosti.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.academy.mapainkluzyvnosti.data.model.PlaceRequestStatus
import com.academy.mapainkluzyvnosti.data.repository.ExistenceReport
import com.academy.mapainkluzyvnosti.ui.components.MapaCard
import com.academy.mapainkluzyvnosti.ui.components.formatCheckDate
import com.academy.mapainkluzyvnosti.ui.theme.StatusAccessible
import com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier
import com.academy.mapainkluzyvnosti.ui.theme.StatusUnverified
import org.koin.compose.viewmodel.koinViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------------------
// Вкладка «Звіти»: «Цього місця не існує»
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReportsTab(snackbarHostState: SnackbarHostState, viewModel: AdminReportsViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var confirm by remember { mutableStateOf<Pair<ExistenceReport, ReportAction>?>(null) }

    LaunchedEffect(uiState.message, uiState.errorMessage) {
        (uiState.message ?: uiState.errorMessage)?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val filters = listOf(PlaceRequestStatus.PENDING to "Очікують", null to "Усі")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            filters.forEachIndexed { index, (status, label) ->
                SegmentedButton(
                    selected = uiState.filter == status,
                    onClick = { viewModel.setFilter(status) },
                    shape = SegmentedButtonDefaults.itemShape(index, filters.size)
                ) { Text(label) }
            }
        }
        when {
            uiState.isLoading && uiState.reports.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.reports.isEmpty() -> TabMessage(
                if (uiState.filter == PlaceRequestStatus.PENDING) "Немає звітів, що очікують розгляду" else "Звітів ще немає"
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                items(uiState.reports, key = { it.id }) { report ->
                    ExistenceReportCard(
                        report = report,
                        isActing = uiState.isActing,
                        onAction = { action -> confirm = report to action },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                }
            }
        }
    }

    confirm?.let { (report, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(action.title) },
            text = { Text(action.description(report.placeName)) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        ReportAction.APPROVE -> viewModel.approve(report)
                        ReportAction.REJECT -> viewModel.reject(report)
                        ReportAction.DELETE -> viewModel.delete(report)
                    }
                    confirm = null
                }) { Text("Підтвердити") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Скасувати") } }
        )
    }
}

private enum class ReportAction(val title: String) {
    APPROVE("Схвалити звіт?"),
    REJECT("Відхилити звіт?"),
    DELETE("Видалити звіт?");

    fun description(place: String): String = when (this) {
        APPROVE -> "«$place» буде приховано з мапи для всіх користувачів."
        REJECT -> "«$place» лишиться на мапі, звіт буде позначено відхиленим."
        DELETE -> "Звіт буде видалено назавжди. Саме місце не зміниться."
    }
}

@Composable
private fun ExistenceReportCard(
    report: ExistenceReport,
    isActing: Boolean,
    onAction: (ReportAction) -> Unit,
    modifier: Modifier = Modifier
) {
    MapaCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = report.placeName, style = MaterialTheme.typography.titleSmall)
            report.placeAddress?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val (statusText, statusColor) = when (report.status) {
                PlaceRequestStatus.PENDING -> "Очікує розгляду" to StatusUnverified
                PlaceRequestStatus.APPROVED -> "Схвалено — місце приховано" to StatusAccessible
                PlaceRequestStatus.REJECTED -> "Відхилено" to StatusBarrier
            }
            Text(text = statusText, style = MaterialTheme.typography.labelMedium, color = statusColor, modifier = Modifier.padding(top = 4.dp))

            val author = listOfNotNull(report.reporterName?.takeIf { it.isNotBlank() }, report.reporterEmail).joinToString(" · ")
            Text(
                text = "Автор: ${author.ifBlank { "невідомо" }}" + (formatCheckDate(report.createdAt)?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(text = report.comment, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            report.photoUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = "Фото до звіту",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (report.status == PlaceRequestStatus.PENDING) {
                    TextButton(onClick = { onAction(ReportAction.APPROVE) }, enabled = !isActing) { Text("Схвалити") }
                    TextButton(onClick = { onAction(ReportAction.REJECT) }, enabled = !isActing) { Text("Відхилити") }
                }
                TextButton(onClick = { onAction(ReportAction.DELETE) }, enabled = !isActing) {
                    Text("Видалити звіт", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Вкладка «Аналітика»: метрики за місяць
// ---------------------------------------------------------------------------

private val MonthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("uk"))

@Composable
fun AdminStatsTab(viewModel: AdminStatsViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = viewModel::previousMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Попередній місяць")
            }
            Text(
                text = uiState.month.format(MonthFormatter).replaceFirstChar { it.titlecase(Locale.forLanguageTag("uk")) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = viewModel::nextMonth, enabled = uiState.canGoForward) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Наступний місяць")
            }
        }

        val stats = uiState.stats
        when {
            uiState.isLoading -> Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.errorMessage != null -> Column(modifier = Modifier.padding(top = 24.dp)) {
                Text(text = uiState.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::load) { Text("Спробувати ще раз") }
            }
            stats != null -> MapaCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val rows = listOf(
                        "Нові користувачі" to stats.newUsers.toString(),
                        "Нові заявки на місця" to stats.requestsTotal.toString(),
                        "   з них схвалено" to stats.requestsApproved.toString(),
                        "   з них відхилено" to stats.requestsRejected.toString(),
                        "Нові відгуки" to stats.newReviews.toString(),
                        "Звіти «місце не існує»" to stats.existenceReportsTotal.toString(),
                        "   з них схвалено" to stats.existenceReportsApproved.toString()
                    )
                    rows.forEachIndexed { index, (label, value) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(text = value, style = MaterialTheme.typography.titleMedium)
                        }
                        if (index < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
