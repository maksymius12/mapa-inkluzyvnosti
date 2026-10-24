package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole

@Composable
private fun mapaSegmentedColors(): SegmentedButtonColors = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primary,
    activeContentColor = Color.White,
    activeBorderColor = MaterialTheme.colorScheme.primary,
    inactiveContainerColor = MaterialTheme.colorScheme.surface,
    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
)

/**
 * Мета користування (мешканець/волонтер) і вікова категорія (учень/дорослий).
 * Від них залежать права: наприклад, фото місць додають лише дорослі, бали за перевірки — учням.
 */
@Composable
fun RoleAndAgeSelector(
    purposeRole: UserPurposeRole,
    onPurposeRoleChange: (UserPurposeRole) -> Unit,
    ageGroup: UserAgeGroup,
    onAgeGroupChange: (UserAgeGroup) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showHints: Boolean = true
) {
    val colors = mapaSegmentedColors()
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "Мета користування", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            SegmentedButton(
                selected = purposeRole == UserPurposeRole.RESIDENT,
                onClick = { onPurposeRoleChange(UserPurposeRole.RESIDENT) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = colors
            ) { Text("Мешканець") }
            SegmentedButton(
                selected = purposeRole == UserPurposeRole.VOLUNTEER,
                onClick = { onPurposeRoleChange(UserPurposeRole.VOLUNTEER) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = colors
            ) { Text("Волонтер") }
        }
        if (showHints) {
            Hint(
                if (purposeRole == UserPurposeRole.VOLUNTEER) {
                    "Волонтери бачать запити про допомогу поруч і можуть їх закривати"
                } else {
                    "Шукайте доступні місця й будуйте зручні маршрути"
                }
            )
        }

        Text(
            text = "Вікова категорія",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 20.dp)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            SegmentedButton(
                selected = ageGroup == UserAgeGroup.STUDENT,
                onClick = { onAgeGroupChange(UserAgeGroup.STUDENT) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = colors
            ) { Text("Учень") }
            SegmentedButton(
                selected = ageGroup == UserAgeGroup.ADULT,
                onClick = { onAgeGroupChange(UserAgeGroup.ADULT) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = colors
            ) { Text("Дорослий") }
        }
        if (showHints) {
            Hint(
                if (ageGroup == UserAgeGroup.STUDENT) {
                    "Учні отримують бали за перевірки; фото місць можуть додавати лише дорослі"
                } else {
                    "Дорослі можуть додавати фото місць"
                }
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}
