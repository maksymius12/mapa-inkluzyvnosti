package com.academy.mapainkluzyvnosti.ui.screens.photoupload

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.ui.components.DemoGateDialog
import com.academy.mapainkluzyvnosti.ui.components.MapaCard
import com.academy.mapainkluzyvnosti.ui.components.PhotoSourceButtons
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import com.academy.mapainkluzyvnosti.ui.components.SecondaryButton
import com.academy.mapainkluzyvnosti.ui.components.color
import com.academy.mapainkluzyvnosti.ui.components.label
import com.academy.mapainkluzyvnosti.ui.components.rememberPhotoPicker
import com.academy.mapainkluzyvnosti.ui.components.toJpegBytes
import com.academy.mapainkluzyvnosti.ui.theme.StatusAccessible
import com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier
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
        scope.launch { viewModel.onPhotoPicked(bitmap.toJpegBytes()) }
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
            val picking = uiState.step == PhotoStep.PICK
            // Перемикач угорі: з ШІ-аналізом чи без.
            val modes = listOf(true to "З ШІ-аналізом", false to "Без ШІ")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (withAi, label) ->
                    SegmentedButton(
                        selected = uiState.withAi == withAi,
                        onClick = { viewModel.setWithAi(withAi) },
                        enabled = picking,
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                    ) { Text(label) }
                }
            }
            Text(
                text = if (uiState.withAi) {
                    "Сфотографуйте вхід — ШІ опише, що видно, і запропонує значення 7 критеріїв. Ви вирішите, чи застосувати їх."
                } else {
                    "Фото просто завантажиться, без аналізу. Дані місця не зміняться."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )

            previewBitmap?.takeIf { !picking }?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Вибране фото",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            when (uiState.step) {
                PhotoStep.PICK -> PhotoSourceButtons(
                    onGallery = photoPicker::pickFromGallery,
                    onCamera = photoPicker::takePhoto,
                    modifier = Modifier.padding(top = 16.dp)
                )
                PhotoStep.UPLOADING -> Progress("Завантажуємо фото…")
                PhotoStep.ANALYZING -> Progress("ШІ аналізує фото…")
                PhotoStep.SAVING -> Progress("Зберігаємо…")
                PhotoStep.ANALYSIS_FAILED -> {
                    Text(
                        text = "ШІ-аналіз зараз не вдався. Можна завантажити фото без аналізу — дані місця не зміняться.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    PrimaryButton(text = "Завантажити без аналізу", onClick = viewModel::saveOnly, modifier = Modifier.padding(top = 12.dp))
                    TextButton(onClick = viewModel::discard) { Text("Скасувати") }
                }
                PhotoStep.REVIEW -> ReviewSection(
                    state = uiState,
                    onApply = viewModel::applyAndSave,
                    onSaveOnly = viewModel::saveOnly,
                    onDiscard = viewModel::discard
                )
                PhotoStep.DONE -> {
                    Text(
                        text = uiState.doneMessage.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    PrimaryButton(text = "Готово", onClick = onDone, modifier = Modifier.padding(top = 16.dp))
                }
            }

            uiState.errorMessage?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun Progress(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(text = text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ReviewSection(
    state: PhotoUploadUiState,
    onApply: () -> Unit,
    onSaveOnly: () -> Unit,
    onDiscard: () -> Unit
) {
    val analysis = state.analysis ?: return
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text(text = "Що бачить ШІ", style = MaterialTheme.typography.titleMedium)
        Text(text = analysis.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        if (analysis.summary.isNotBlank()) {
            Text(text = analysis.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        Text(
            text = "Оцінка ШІ: ${analysis.status.label()}",
            color = analysis.status.color(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp)
        )

        Text(text = "Критерії", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        MapaCard(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            state.proposals.forEachIndexed { index, p ->
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = p.criterion.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        val (verdictText, verdictColor) = when (p.verdict) {
                            AiVerdict.YES -> "Виконано" to StatusAccessible
                            AiVerdict.NO -> "Не виконано" to StatusBarrier
                            AiVerdict.NOT_VISIBLE -> "Не видно" to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(text = verdictText, color = verdictColor, style = MaterialTheme.typography.labelLarge)
                    }
                    if (p.reason.isNotBlank()) {
                        Text(text = p.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    criterionChangeNote(p)?.let { note ->
                        Text(text = note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                if (index < state.proposals.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        if (analysis.suggestions.isNotEmpty()) {
            Text(text = "Рекомендації", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            analysis.suggestions.forEach { Text(text = "• $it", modifier = Modifier.padding(top = 4.dp)) }
        }

        Text(
            text = when {
                !state.hasHumanCheck ->
                    "Місце ще не перевіряла людина — ШІ не може виставити критерії сам. Завантажте фото й пройдіть перевірку."
                state.loweredCount == 0 ->
                    "ШІ не знайшов, що знизити: критерії лишаться як є, буде збережено оцінку ШІ."
                else -> "«Замінити дані» змінить ${state.loweredCount} критерій(-ї) на «Ні». ШІ може лише знизити оцінку, не підвищити."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
        PrimaryButton(
            text = "Замінити дані й завантажити фото",
            onClick = onApply,
            enabled = state.hasHumanCheck,
            modifier = Modifier.padding(top = 8.dp)
        )
        SecondaryButton(text = "Лише завантажити фото", onClick = onSaveOnly, modifier = Modifier.padding(top = 8.dp))
        TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) { Text("Скасувати, не завантажувати") }
    }
}

/** Що станеться з критерієм після «Замінити дані». */
private fun criterionChangeNote(p: CriterionProposal): String? = when {
    p.current == null -> null
    p.current && p.proposed == false -> "Буде: «Так» → «Ні»"
    !p.current && p.verdict == AiVerdict.YES -> "Лишиться «Ні»: підвищити може лише перевірка людиною"
    else -> null
}
