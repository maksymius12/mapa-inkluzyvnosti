package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.CheckResult

data class QuickCheckState(
    val ramp: Boolean = false,
    val doorWidth: Boolean = false,
    val threshold: Boolean = false,
    val elevator: Boolean = false,
    val toilet: Boolean = false,
    val tactile: Boolean = false,
    val staffAssistance: Boolean = false,
    val comment: String = ""
) {
    fun toCheckResult() = CheckResult(
        ramp = ramp,
        doorWidth = doorWidth,
        threshold = threshold,
        elevator = elevator,
        toilet = toilet,
        tactile = tactile,
        staffAssistance = staffAssistance
    )
}

private data class CriterionRow(val label: String, val value: Boolean, val onChange: (Boolean) -> Unit)

@Composable
fun QuickCheckForm(
    state: QuickCheckState,
    onStateChange: (QuickCheckState) -> Unit,
    modifier: Modifier = Modifier
) {
    val criteria = listOf(
        CriterionRow("Пандус на вході", state.ramp) { onStateChange(state.copy(ramp = it)) },
        CriterionRow("Ширина дверей (≥80 см)", state.doorWidth) { onStateChange(state.copy(doorWidth = it)) },
        CriterionRow("Немає порогів/перепадів", state.threshold) { onStateChange(state.copy(threshold = it)) },
        CriterionRow("Ліфт (за потреби)", state.elevator) { onStateChange(state.copy(elevator = it)) },
        CriterionRow("Доступний туалет", state.toilet) { onStateChange(state.copy(toilet = it)) },
        CriterionRow("Тактильні позначки", state.tactile) { onStateChange(state.copy(tactile = it)) },
        CriterionRow("Допомога персоналу", state.staffAssistance) { onStateChange(state.copy(staffAssistance = it)) }
    )

    Column(modifier = modifier.fillMaxWidth()) {
        criteria.forEach { criterion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = criterion.label, style = MaterialTheme.typography.bodyLarge)
                Switch(checked = criterion.value, onCheckedChange = criterion.onChange)
            }
        }
        OutlinedTextField(
            value = state.comment,
            onValueChange = { onStateChange(state.copy(comment = it)) },
            label = { Text("Коментар (необов'язково)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}
