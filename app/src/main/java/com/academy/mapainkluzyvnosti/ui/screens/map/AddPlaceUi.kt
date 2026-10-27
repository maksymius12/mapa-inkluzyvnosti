package com.academy.mapainkluzyvnosti.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.ui.components.PlaceFormFields
import com.academy.mapainkluzyvnosti.ui.components.PlaceFormState
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import com.academy.mapainkluzyvnosti.ui.components.SecondaryButton
import com.academy.mapainkluzyvnosti.ui.components.formLabel

/** Плаваюча кнопка «+ Додати місце»: синя «пігулка», як основні кнопки макета. */
@Composable
fun AddPlaceButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 18.dp, top = 11.dp, bottom = 11.dp)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(
            text = "Додати місце",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/**
 * Приціл по центру мапи: вістря шпильки — точно в центрі (туди дивиться камера).
 * Не має обробників дотиків, тож мапа під ним рухається як завжди.
 */
@Composable
fun CenterPin(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
        )
        Icon(
            Icons.Filled.Place,
            contentDescription = "Позиція нового місця",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(48.dp)
                .offset(y = (-22).dp)
        )
    }
}

/** Режим вибору позиції: підказка вгорі, кнопки «Підтвердити позицію» та «Скасувати» внизу. */
@Composable
fun LocationPickerControls(onConfirm: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        val hintShape = RoundedCornerShape(16.dp)
        Text(
            text = "Перемістіть карту, щоб вказати місце",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .shadow(6.dp, hintShape)
                .clip(hintShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, hintShape)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
        Text(
            text = "Порада: довге натискання на мапу ставить маркер у те місце",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 76.dp, start = 32.dp, end = 32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            PrimaryButton(text = "Підтвердити позицію", onClick = onConfirm)
            SecondaryButton(
                text = "Скасувати",
                onClick = onCancel,
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            )
        }
    }
}

/** Нижня панель з формою нового місця. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaceSheet(
    form: PlaceFormState,
    isAddressLoading: Boolean,
    isSubmitting: Boolean,
    submitError: String?,
    onFormChange: (PlaceFormState) -> Unit,
    onSubmit: () -> Unit,
    onChangeLocation: () -> Unit,
    onDismiss: () -> Unit,
    isResidentRequest: Boolean = false
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
            Text(text = "Нове місце", style = MaterialTheme.typography.titleLarge)
            Text(
                text = if (isResidentRequest) {
                    "Заявку розгляне волонтер, учень або адміністратор — після схвалення місце з'явиться на мапі."
                } else {
                    "Після додавання місце з'явиться на мапі зі статусом «Не перевірено»."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            PlaceFormFields(
                state = form,
                onChange = onFormChange,
                isAddressLoading = isAddressLoading,
                enabled = !isSubmitting
            )
            if (submitError != null) {
                Text(
                    text = submitError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            PrimaryButton(
                text = "Додати",
                onClick = onSubmit,
                loading = isSubmitting,
                modifier = Modifier.padding(top = 16.dp)
            )
            TextButton(
                onClick = onChangeLocation,
                enabled = !isSubmitting,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
            ) {
                Text("Змінити позицію")
            }
        }
    }
}

/** «Схоже місце вже є»: перелік схожих місць поруч, скасувати або все одно додати. */
@Composable
fun DuplicatePlacesDialog(places: List<Place>, onCancel: () -> Unit, onAddAnyway: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Схоже місце вже є") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Поруч (до 30 м) уже є місце з такою самою або дуже схожою назвою:")
                places.take(5).forEach { place ->
                    Column {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOf(place.category.formLabel, place.address).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAddAnyway) { Text("Все одно додати") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Скасувати") } }
    )
}

/** Мешканець подав заявку: місце з'явиться на мапі лише після розгляду волонтером/учнем/адміном. */
@Composable
fun RequestSubmittedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Заявку надіслано") },
        text = {
            Text("Дякуємо! Заявку на нову локацію передано на розгляд волонтерам, учням та адміністратору. Статус можна перевірити в розділі «Мої заявки» у профілі.")
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Зрозуміло") } }
    )
}

/** «Місце додано» з пропозицією одразу пройти перевірку за 7 критеріями. */
@Composable
fun PlaceAddedDialog(place: Place, hiddenByFilters: Boolean, onCheck: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Місце додано") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("«${place.name}» уже на мапі зі статусом «Не перевірено». Пройдіть перевірку доступності, щоб інші знали, чи зручно туди дістатися.")
                if (hiddenByFilters) {
                    Text(
                        text = "Зараз його ховають фільтри мапи — увімкніть усі типи та категорії, щоб побачити.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onCheck) { Text("Пройти перевірку доступності") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Пізніше") } }
    )
}
