package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.ReferralSource
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
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                colors = colors
            ) { Text("Мешканець") }
            SegmentedButton(
                selected = purposeRole == UserPurposeRole.VOLUNTEER,
                onClick = { onPurposeRoleChange(UserPurposeRole.VOLUNTEER) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                colors = colors
            ) { Text("Волонтер") }
            SegmentedButton(
                selected = purposeRole == UserPurposeRole.STUDENT,
                onClick = { onPurposeRoleChange(UserPurposeRole.STUDENT) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                colors = colors
            ) { Text("Учень") }
        }
        if (showHints) {
            Hint(
                when (purposeRole) {
                    UserPurposeRole.VOLUNTEER, UserPurposeRole.STUDENT ->
                        "Волонтери й учні бачать запити про допомогу та заявки на нові місця поруч і можуть їх розглядати"
                    UserPurposeRole.RESIDENT ->
                        "Шукайте доступні місця, будуйте зручні маршрути; нову локацію подаєте як заявку на розгляд"
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

private val ReferralSource.label: String
    get() = when (this) {
        ReferralSource.SCHOOL -> "Через школу / вчителя"
        ReferralSource.FRIENDS -> "Від друзів чи знайомих"
        ReferralSource.SOCIAL_MEDIA -> "Соціальні мережі"
        ReferralSource.SEARCH -> "Пошук в інтернеті"
        ReferralSource.OTHER -> "Інше"
    }

/** «Звідки ви про нас дізналися» — питається один раз при реєстрації, необов'язково. */
@Composable
fun ReferralSourceSelector(
    value: ReferralSource?,
    onChange: (ReferralSource) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "Звідки ви про нас дізналися?", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Необов'язково — допоможе нам розвивати проєкт",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
        )
        Column(modifier = Modifier.selectableGroup()) {
            ReferralSource.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { onChange(option) }
                ) {
                    RadioButton(selected = value == option, onClick = { onChange(option) }, enabled = enabled)
                    Text(text = option.label, modifier = Modifier.padding(start = 4.dp))
                }
            }
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
