package com.academy.mapainkluzyvnosti.ui.screens.placedetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.DayHours
import com.academy.mapainkluzyvnosti.data.model.OpeningHours
import com.academy.mapainkluzyvnosti.data.model.formatMinutes
import com.academy.mapainkluzyvnosti.data.model.parseMinutes
import com.academy.mapainkluzyvnosti.ui.components.MapaCard
import com.academy.mapainkluzyvnosti.ui.components.SecondaryButton
import com.academy.mapainkluzyvnosti.ui.theme.StatusAccessible
import com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier
import java.time.DayOfWeek
import java.time.ZonedDateTime

private val DAY_NAMES = mapOf(
    DayOfWeek.MONDAY to "Понеділок",
    DayOfWeek.TUESDAY to "Вівторок",
    DayOfWeek.WEDNESDAY to "Середа",
    DayOfWeek.THURSDAY to "Четвер",
    DayOfWeek.FRIDAY to "П'ятниця",
    DayOfWeek.SATURDAY to "Субота",
    DayOfWeek.SUNDAY to "Неділя"
)

/** «Графік роботи» на картці місця: «Відчинено зараз» / «Зачинено» за київським часом і розклад на тиждень. */
@Composable
fun OpeningHoursSection(
    hours: OpeningHours?,
    canEdit: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = "Графік роботи", style = MaterialTheme.typography.titleMedium)
        if (hours == null) {
            Text(
                text = "Графік не вказано",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            val now = ZonedDateTime.now(OpeningHours.KYIV)
            val open = hours.isOpen(now)
            Text(
                text = if (open) "Відчинено зараз" else "Зачинено",
                color = if (open) StatusAccessible else StatusBarrier,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
            MapaCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    if (hours.always) {
                        Text("Цілодобово, без вихідних", modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        DayOfWeek.entries.forEach { day ->
                            val isToday = day == now.dayOfWeek
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(
                                    text = DAY_NAMES.getValue(day),
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = hours.days[day]?.text ?: "Вихідний",
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
        if (canEdit) {
            SecondaryButton(
                text = if (hours == null) "Додати графік" else "Змінити графік",
                onClick = onEdit,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private class DayDraft(closed: Boolean, open: String, close: String) {
    var closed by mutableStateOf(closed)
    var open by mutableStateOf(open)
    var close by mutableStateOf(close)
}

/** Редактор графіка: по днях (відкриття / закриття / вихідний) або «Цілодобово». */
@Composable
fun OpeningHoursDialog(
    initial: OpeningHours?,
    isSaving: Boolean,
    error: String?,
    onSave: (OpeningHours?) -> Unit,
    onDismiss: () -> Unit
) {
    var always by remember { mutableStateOf(initial?.always == true) }
    val drafts = remember {
        mutableStateListOf<DayDraft>().apply {
            DayOfWeek.entries.forEach { day ->
                val h = initial?.days?.get(day)
                val weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
                add(
                    if (initial == null || initial.always) {
                        DayDraft(closed = weekend, open = "09:00", close = "18:00")
                    } else {
                        DayDraft(closed = h == null, open = h?.let { formatMinutes(it.openMin) } ?: "09:00", close = h?.let { formatMinutes(it.closeMin) } ?: "18:00")
                    }
                )
            }
        }
    }
    fun valid(d: DayDraft): Boolean {
        if (d.closed) return true
        val o = parseMinutes(d.open)
        val c = parseMinutes(d.close)
        return o != null && c != null && o < 1440 && c > 0 && o != c
    }
    val allValid = always || drafts.all(::valid)

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Графік роботи") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Цілодобово", modifier = Modifier.weight(1f))
                    Switch(checked = always, onCheckedChange = { always = it }, enabled = !isSaving)
                }
                if (!always) {
                    Text(
                        text = "Час у форматі ГГ:ХХ. Якщо закриття раніше за відкриття — працює після півночі.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DayOfWeek.entries.forEachIndexed { index, day ->
                        val d = drafts[index]
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(DAY_NAMES.getValue(day), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Checkbox(checked = d.closed, onCheckedChange = { d.closed = it }, enabled = !isSaving)
                                Text("Вихідний")
                            }
                            if (!d.closed) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TimeField(value = d.open, label = "Відкриття", enabled = !isSaving) { d.open = it }
                                    Text("–", modifier = Modifier.padding(horizontal = 8.dp))
                                    TimeField(value = d.close, label = "Закриття", enabled = !isSaving) { d.close = it }
                                }
                                if (!valid(d)) {
                                    Text("Перевірте час", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            val mon = drafts.first()
                            drafts.drop(1).take(4).forEach { it.closed = mon.closed; it.open = mon.open; it.close = mon.close }
                        },
                        enabled = !isSaving
                    ) { Text("Як понеділок — на вівторок–п'ятницю") }
                }
                if (initial != null) {
                    TextButton(onClick = { onSave(null) }, enabled = !isSaving) {
                        Text("Прибрати графік", color = MaterialTheme.colorScheme.error)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val hours = if (always) {
                        OpeningHours(true, emptyMap())
                    } else {
                        OpeningHours(false, DayOfWeek.entries.mapIndexed { i, day ->
                            val d = drafts[i]
                            day to if (d.closed) null else DayHours(parseMinutes(d.open)!!, parseMinutes(d.close)!!)
                        }.toMap())
                    }
                    onSave(hours)
                },
                enabled = allValid && !isSaving
            ) { Text(if (isSaving) "Зберігаємо…" else "Зберегти") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Скасувати") } }
    )
}

@Composable
private fun TimeField(value: String, label: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == ':' }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(110.dp)
    )
}
