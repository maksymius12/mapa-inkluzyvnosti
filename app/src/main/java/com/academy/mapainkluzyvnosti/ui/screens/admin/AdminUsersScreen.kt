package com.academy.mapainkluzyvnosti.ui.screens.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.data.model.UserRole
import com.academy.mapainkluzyvnosti.data.repository.AdminUser
import com.academy.mapainkluzyvnosti.ui.components.MapaCard
import com.academy.mapainkluzyvnosti.ui.screens.settings.ageGroupLabel
import com.academy.mapainkluzyvnosti.ui.screens.settings.purposeRoleLabel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Мінімальна адмін-панель: список користувачів (пошук за поштою чи ім'ям), роль «Користувач»/«Адмін»
 * з підтвердженням і мета користування. Сервер окремо перевіряє права (RPC admin_*).
 */
@Composable
fun AdminUsersScreen(onBack: () -> Unit, viewModel: AdminUsersViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var roleChange by remember { mutableStateOf<Pair<AdminUser, UserRole>?>(null) }

    LaunchedEffect(uiState.message, uiState.errorMessage) {
        (uiState.message ?: uiState.errorMessage)?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                text = "Адмін-панель",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (!uiState.isAdmin) {
            CenteredText("Недостатньо прав: розділ доступний лише адміністраторам")
        } else {
            OutlinedTextField(
                value = uiState.search,
                onValueChange = viewModel::onSearchChange,
                label = { Text("Пошук за поштою або ім'ям") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            when {
                uiState.isLoading && uiState.users.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                uiState.users.isEmpty() -> CenteredText("Користувачів не знайдено")
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(uiState.users, key = { it.id }) { user ->
                        UserCard(
                            user = user,
                            isSelf = user.id == uiState.currentUserId,
                            isActing = uiState.isActing,
                            onRoleChange = { role -> if (role != user.role) roleChange = user to role },
                            onPurposeChange = { viewModel.setPurpose(user, it) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }

    roleChange?.let { (user, role) ->
        val who = user.name?.takeIf { it.isNotBlank() } ?: user.email ?: "користувача"
        AlertDialog(
            onDismissRequest = { roleChange = null },
            title = { Text(if (role == UserRole.ADMIN) "Призначити адміністратором?" else "Зняти роль адміністратора?") },
            text = {
                Text(
                    if (role == UserRole.ADMIN) {
                        "$who отримає повний доступ: модерацію місць, заявок і керування користувачами."
                    } else {
                        "$who втратить доступ до адмін-панелі та модерації."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setRole(user, role)
                    roleChange = null
                }) { Text("Підтвердити") }
            },
            dismissButton = { TextButton(onClick = { roleChange = null }) { Text("Скасувати") } }
        )
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserCard(
    user: AdminUser,
    isSelf: Boolean,
    isActing: Boolean,
    onRoleChange: (UserRole) -> Unit,
    onPurposeChange: (UserPurposeRole) -> Unit,
    modifier: Modifier = Modifier
) {
    MapaCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = user.name?.takeIf { it.isNotBlank() } ?: "Без імені",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = listOfNotNull(user.email, user.ageGroup?.let(::ageGroupLabel)).joinToString(" · ")
                    + if (isSelf) " · це ви" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Роль", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            val roles = listOf(UserRole.USER to "Користувач", UserRole.ADMIN to "Адмін")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                roles.forEachIndexed { index, (role, label) ->
                    SegmentedButton(
                        selected = user.role == role,
                        onClick = { onRoleChange(role) },
                        // Власну роль адміна не знімаємо звідси, щоб випадково не втратити доступ.
                        enabled = !isActing && !(isSelf && role == UserRole.USER),
                        shape = SegmentedButtonDefaults.itemShape(index, roles.size)
                    ) { Text(label) }
                }
            }

            Text("Мета", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            val purposes = UserPurposeRole.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                purposes.forEachIndexed { index, purpose ->
                    SegmentedButton(
                        selected = user.purposeRole == purpose,
                        onClick = { onPurposeChange(purpose) },
                        enabled = !isActing,
                        shape = SegmentedButtonDefaults.itemShape(index, purposes.size)
                    ) { Text(purposeRoleLabel(purpose)) }
                }
            }
        }
    }
}
