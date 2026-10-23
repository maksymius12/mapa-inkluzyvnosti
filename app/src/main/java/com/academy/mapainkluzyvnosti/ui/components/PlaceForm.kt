package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.domain.usecase.PlaceInputRules

/** Стан форми місця (додавання й редагування). Помилки показуються після першої спроби зберегти. */
data class PlaceFormState(
    val name: String = "",
    val category: PlaceCategory? = null,
    val address: String = "",
    val showErrors: Boolean = false
) {
    val nameError: String? get() = PlaceInputRules.nameError(name)
    val addressError: String? get() = PlaceInputRules.addressError(address)
    val categoryError: String? get() = if (category == null) "Оберіть категорію" else null
    val isValid: Boolean get() = nameError == null && addressError == null && categoryError == null
}

/** Назви категорій у формі додавання — як у переліку дев'яти категорій закладів. */
val PlaceCategory.formLabel: String
    get() = when (this) {
        PlaceCategory.EDUCATION -> "Освіта"
        PlaceCategory.HEALTH -> "Медицина"
        PlaceCategory.CULTURE -> "Культура"
        PlaceCategory.SPORT -> "Спорт"
        PlaceCategory.CAFE -> "Кафе та ресторани"
        PlaceCategory.SERVICES -> "Сервіси"
        PlaceCategory.ADMIN -> "Адмінустанови"
        PlaceCategory.SHOP -> "Магазини"
        PlaceCategory.TRANSIT -> "Транспорт"
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceFormFields(
    state: PlaceFormState,
    onChange: (PlaceFormState) -> Unit,
    modifier: Modifier = Modifier,
    isAddressLoading: Boolean = false,
    enabled: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val nameError = state.nameError.takeIf { state.showErrors }
        OutlinedTextField(
            value = state.name,
            onValueChange = { onChange(state.copy(name = it.take(PlaceInputRules.NAME_MAX + 20))) },
            label = { Text("Назва") },
            singleLine = true,
            enabled = enabled,
            isError = nameError != null,
            supportingText = {
                Text(nameError ?: "${PlaceInputRules.normalize(state.name).length}/${PlaceInputRules.NAME_MAX}")
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        SectionLabel(text = "Категорія", modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlaceCategory.entries.forEach { category ->
                MapaChip(
                    label = category.formLabel,
                    selected = state.category == category,
                    onClick = { if (enabled) onChange(state.copy(category = category)) }
                )
            }
        }
        val categoryError = state.categoryError.takeIf { state.showErrors }
        if (categoryError != null) {
            Text(
                text = categoryError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }

        val addressError = state.addressError.takeIf { state.showErrors }
        OutlinedTextField(
            value = state.address,
            onValueChange = { onChange(state.copy(address = it.take(PlaceInputRules.ADDRESS_MAX + 20))) },
            label = { Text("Адреса (необов'язково)") },
            singleLine = true,
            enabled = enabled,
            isError = addressError != null,
            supportingText = addressError?.let { error -> { Text(error) } },
            trailingIcon = if (isAddressLoading) {
                { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )
    }
}
