package com.academy.mapainkluzyvnosti.ui.screens.placedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.repository.PlaceReportReason
import com.academy.mapainkluzyvnosti.domain.usecase.PlaceInputRules
import com.academy.mapainkluzyvnosti.ui.components.PlaceFormFields
import com.academy.mapainkluzyvnosti.ui.components.PlaceFormState
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton

/** Нижня панель редагування власного місця: назва, категорія, адреса. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaceSheet(
    form: PlaceFormState,
    isSaving: Boolean,
    error: String?,
    onFormChange: (PlaceFormState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Text(
                text = "Змінити місце",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            PlaceFormFields(state = form, onChange = onFormChange, enabled = !isSaving)
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            PrimaryButton(
                text = "Зберегти",
                onClick = onSave,
                loading = isSaving,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun DeletePlaceDialog(placeName: String, isDeleting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text("Видалити місце?") },
        text = { Text("«$placeName» зникне з мапи разом із перевірками, фото й відгуками. Цю дію не можна скасувати.") },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isDeleting) {
                Text("Видалити", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Скасувати") }
        }
    )
}

/** Скарга на чуже місце: причина (дубль, неправильне місце, не існує, інше) і необов'язковий коментар. */
@Composable
fun ReportPlaceDialog(
    isSending: Boolean,
    error: String?,
    onSubmit: (PlaceReportReason, String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by rememberSaveable { mutableStateOf<PlaceReportReason?>(null) }
    var comment by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Поскаржитися") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Що не так із цим місцем?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Column(modifier = Modifier.selectableGroup()) {
                    PlaceReportReason.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSending) { reason = option }
                        ) {
                            RadioButton(selected = reason == option, onClick = { reason = option }, enabled = !isSending)
                            Text(text = option.label, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(PlaceInputRules.REPORT_COMMENT_MAX) },
                    label = { Text("Коментар (необов'язково)") },
                    enabled = !isSending,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
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
            TextButton(
                onClick = { reason?.let { onSubmit(it, comment) } },
                enabled = reason != null && !isSending
            ) { Text(if (isSending) "Надсилаємо…" else "Надіслати") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text("Скасувати") }
        }
    )
}
