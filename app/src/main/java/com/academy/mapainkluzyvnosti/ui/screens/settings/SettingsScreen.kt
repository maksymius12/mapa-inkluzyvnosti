package com.academy.mapainkluzyvnosti.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.ui.components.CategoryGroup
import com.academy.mapainkluzyvnosti.ui.components.MapaCard
import com.academy.mapainkluzyvnosti.ui.components.MapaChip
import com.academy.mapainkluzyvnosti.ui.components.MapaSwitch
import com.academy.mapainkluzyvnosti.ui.components.ScreenHeader
import com.academy.mapainkluzyvnosti.ui.components.SecondaryButton
import com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier
import org.koin.compose.viewmodel.koinViewModel

/** Налаштування: тема, сповіщення, розмір тексту, категорії за замовчуванням, керування акаунтом. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onSignedOut: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Налаштування", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionTitle("Вигляд")
            MapaCard(modifier = Modifier.fillMaxWidth()) {
                SwitchRow(title = "Темна тема", checked = isDarkTheme, onCheckedChange = { viewModel.toggleTheme() })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = "Розмір тексту",
                    subtitle = "Збільшений шрифт у всьому застосунку",
                    checked = settings.isLargeText,
                    onCheckedChange = viewModel::setLargeText
                )
            }

            SectionTitle("Сповіщення")
            MapaCard(modifier = Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = "SOS-запити поблизу",
                    subtitle = "Сповіщення про нові запити про допомогу",
                    checked = settings.sosNotificationsEnabled,
                    onCheckedChange = viewModel::setSosNotifications
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = "Оновлення збережених маршрутів",
                    subtitle = "Якщо на маршруті з'явилась нова перешкода",
                    checked = settings.routeNotificationsEnabled,
                    onCheckedChange = viewModel::setRouteNotifications
                )
            }

            SectionTitle("Мапа")
            MapaCard(modifier = Modifier.fillMaxWidth()) {
                ClickableRow(
                    title = "Категорії за замовчуванням",
                    subtitle = "${settings.defaultCategories.size} з ${PlaceCategory.entries.size} показуються після скидання фільтрів",
                    onClick = viewModel::openCategoryPicker
                )
            }

            if (!isDemoMode) {
                SectionTitle("Керування акаунтом")
                MapaCard(modifier = Modifier.fillMaxWidth()) {
                    ClickableRow(title = "Змінити пароль", onClick = viewModel::openChangePassword)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ClickableRow(
                        title = "Видалити акаунт",
                        titleColor = StatusBarrier,
                        onClick = viewModel::openDeleteAccountConfirm
                    )
                }
                dialogState.passwordChangeMessage?.takeIf { !dialogState.showChangePassword }?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            SecondaryButton(
                text = "Вийти",
                icon = Icons.AutoMirrored.Filled.Logout,
                onClick = { viewModel.signOut(onSignedOut) },
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp).navigationBarsPadding()
            )
        }
    }

    if (dialogState.showCategoryPicker) {
        CategoryPickerDialog(
            selected = settings.defaultCategories,
            onToggle = viewModel::toggleDefaultGroup,
            onDismiss = viewModel::dismissCategoryPicker
        )
    }

    if (dialogState.showChangePassword) {
        ChangePasswordDialog(
            password = dialogState.newPassword,
            isSubmitting = dialogState.isChangingPassword,
            errorMessage = dialogState.passwordChangeMessage,
            onPasswordChange = viewModel::onNewPasswordChange,
            onDismiss = viewModel::dismissChangePassword,
            onSubmit = viewModel::submitPasswordChange
        )
    }

    if (dialogState.showDeleteAccountConfirm) {
        DeleteAccountDialog(
            isDeleting = dialogState.isDeletingAccount,
            errorMessage = dialogState.deleteAccountError,
            onDismiss = viewModel::dismissDeleteAccountConfirm,
            onConfirm = { viewModel.confirmDeleteAccount(onSignedOut) }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        MapaSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ClickableRow(
    title: String,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickerDialog(selected: Set<PlaceCategory>, onToggle: (CategoryGroup) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Категорії за замовчуванням") },
        text = {
            Column {
                Text(
                    text = "Ці категорії показуються на мапі, коли фільтри скинуті",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryGroup.entries.forEach { group ->
                        MapaChip(
                            label = group.label,
                            selected = group.categories.all { it in selected },
                            onClick = { onToggle(group) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        }
    )
}

@Composable
private fun ChangePasswordDialog(
    password: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новий пароль") },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Новий пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !isSubmitting) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("Зберегти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Скасувати") }
        }
    )
}

@Composable
private fun DeleteAccountDialog(
    isDeleting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Видалити акаунт?") },
        text = {
            Column {
                Text("Це незворотно видалить твій акаунт і всі пов'язані дані. Скасувати цю дію неможливо.")
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isDeleting) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("Видалити", color = StatusBarrier)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Скасувати") }
        }
    )
}
