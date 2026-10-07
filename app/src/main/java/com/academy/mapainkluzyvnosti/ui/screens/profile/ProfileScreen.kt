package com.academy.mapainkluzyvnosti.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.ui.components.CategoryChip
import com.academy.mapainkluzyvnosti.ui.theme.AccentPrimary
import com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier
import com.academy.mapainkluzyvnosti.ui.theme.StatusPartial
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onSignedOut: () -> Unit = {}, viewModel: ProfileViewModel = koinViewModel()) {
    val user by viewModel.currentUser.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Профіль") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = AccentPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }

            Text(
                text = if (isDemoMode) "Гостьовий перегляд" else (user?.name ?: "—"),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp)
            )

            if (isDemoMode) {
                Text(
                    text = "Дані доступні лише для перегляду. Увійди через Google, щоб додавати перевірки, фото, обране та SOS.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = purposeRoleLabel(user?.purposeRole),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = " · ${ageGroupLabel(user?.ageGroup)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                if (user?.ageGroup == UserAgeGroup.STUDENT) {
                    GamificationBlock(points = user?.points ?: 0, checksCount = user?.checksCount ?: 0)
                }
            }

            SectionHeader("Налаштування")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SwitchRow(title = "Темна тема", checked = isDarkTheme, onCheckedChange = { viewModel.toggleTheme() })
                    HorizontalDivider()
                    SwitchRow(
                        title = "SOS-запити поблизу",
                        subtitle = "Сповіщення про нові запити про допомогу",
                        checked = settings.sosNotificationsEnabled,
                        onCheckedChange = viewModel::setSosNotifications
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = "Оновлення збережених маршрутів",
                        subtitle = "Якщо на маршруті з'явилась нова перешкода",
                        checked = settings.routeNotificationsEnabled,
                        onCheckedChange = viewModel::setRouteNotifications
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = "Збільшений текст",
                        subtitle = "Крупніший шрифт у всьому застосунку",
                        checked = settings.isLargeText,
                        onCheckedChange = viewModel::setLargeText
                    )
                    HorizontalDivider()
                    ClickableRow(
                        title = "Категорії за замовчуванням",
                        subtitle = "${settings.defaultCategories.size} з ${PlaceCategory.entries.size} показуються при вході",
                        onClick = viewModel::openCategoryPicker
                    )
                }
            }

            if (!isDemoMode) {
                SectionHeader("Керування акаунтом")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        ClickableRow(title = "Змінити пароль", onClick = viewModel::openChangePassword)
                        HorizontalDivider()
                        ClickableRow(
                            title = "Видалити акаунт",
                            titleColor = StatusBarrier,
                            onClick = viewModel::openDeleteAccountConfirm
                        )
                    }
                }
                dialogState.passwordChangeMessage?.takeIf { !dialogState.showChangePassword }?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            OutlinedButton(
                onClick = { viewModel.signOut(onSignedOut) },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text(" Вийти", modifier = Modifier.padding(start = 4.dp))
            }
        }
    }

    if (dialogState.showCategoryPicker) {
        CategoryPickerDialog(
            selected = settings.defaultCategories,
            onToggle = viewModel::toggleDefaultCategory,
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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ClickableRow(
    title: String,
    subtitle: String? = null,
    titleColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickerDialog(selected: Set<PlaceCategory>, onToggle: (PlaceCategory) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Категорії за замовчуванням") },
        text = {
            Column {
                Text(
                    text = "Ці категорії будуть показані на карті одразу при вході",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlaceCategory.entries.forEach { category ->
                        CategoryChip(
                            category = category,
                            selected = category in selected,
                            onClick = { onToggle(category) }
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

@Composable
private fun GamificationBlock(points: Int, checksCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Твій внесок", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn(value = points.toString(), label = "Балів")
                StatColumn(value = checksCount.toString(), label = "Перевірок")
                StatColumn(value = badgeCount(checksCount).toString(), label = "Значків")
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = Icons.Filled.EmojiEvents, contentDescription = null, tint = StatusPartial)
        Text(text = value, style = MaterialTheme.typography.titleLarge)
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun badgeCount(checksCount: Int): Int = checksCount / 5

private fun purposeRoleLabel(role: UserPurposeRole?): String = when (role) {
    UserPurposeRole.RESIDENT -> "Мешканець"
    UserPurposeRole.VOLUNTEER -> "Волонтер"
    null -> "—"
}

private fun ageGroupLabel(group: UserAgeGroup?): String = when (group) {
    UserAgeGroup.STUDENT -> "Учень"
    UserAgeGroup.ADULT -> "Дорослий"
    null -> "—"
}
