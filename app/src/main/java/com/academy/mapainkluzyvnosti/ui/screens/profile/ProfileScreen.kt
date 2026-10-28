package com.academy.mapainkluzyvnosti.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.academy.mapainkluzyvnosti.BuildConfig
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.academy.mapainkluzyvnosti.ui.components.MapaCard
import com.academy.mapainkluzyvnosti.ui.components.SecondaryButton
import com.academy.mapainkluzyvnosti.ui.screens.settings.ageGroupLabel
import com.academy.mapainkluzyvnosti.ui.screens.settings.purposeRoleLabel
import com.academy.mapainkluzyvnosti.ui.theme.StatusPartial
import org.koin.compose.viewmodel.koinViewModel

/** Екран 7: профіль. Аватар, ім'я з реального профілю, список розділів. */
@Composable
fun ProfileScreen(
    onOpenRoutes: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenPlaceRequests: () -> Unit,
    onOpenAdmin: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel()
) {
    val user by viewModel.currentUser.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    var showAbout by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    val name = if (isDemoMode) "Гостьовий перегляд" else viewModel.displayName(user)
    // Мета й вікова категорія — з profiles (або підставлені, поки рядка немає).
    val subtitle = user?.takeIf { !isDemoMode }?.let { "${purposeRoleLabel(it.purposeRole)} · ${ageGroupLabel(it.ageGroup)}" }
    val email = if (isDemoMode) null else viewModel.email(user)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name = name, imageUrl = if (isDemoMode) null else viewModel.avatarUrl(), isGuest = isDemoMode)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(text = name, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                email?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Налаштування", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (isDemoMode) {
            Text(
                text = "Дані доступні лише для перегляду. Увійди через Google, щоб додавати перевірки, фото, обране та SOS.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            if (user?.rolesNeedReview == true) {
                MapaCard(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text(
                        text = "Мету користування й вікову категорію підставлено за замовчуванням. Перевірте їх у Налаштуваннях.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            if (user?.ageGroup == UserAgeGroup.STUDENT) {
                GamificationBlock(points = user?.points ?: 0, checksCount = user?.checksCount ?: 0)
            }
        }

        Column(modifier = Modifier.padding(top = 24.dp)) {
            MenuRow(Icons.Outlined.Route, "Мої маршрути", onOpenRoutes)
            MenuRow(Icons.Outlined.FavoriteBorder, "Обране", onOpenFavorites)
            MenuRow(Icons.Outlined.NotificationsNone, "Повідомлення", onOpenNotifications)
            if (!isDemoMode) {
                MenuRow(
                    Icons.AutoMirrored.Outlined.Assignment,
                    if (user?.isReviewer == true) "Заявки на розгляд" else "Мої заявки",
                    onOpenPlaceRequests
                )
            }
            if (!isDemoMode && user?.isAdmin == true) {
                MenuRow(Icons.Outlined.AdminPanelSettings, "Адмін-панель", onOpenAdmin)
            }
            MenuRow(Icons.Outlined.Settings, "Налаштування", onOpenSettings)
            MenuRow(Icons.Outlined.HelpOutline, "Допомога", { showHelp = true })
            MenuRow(Icons.Outlined.Info, "Про застосунок", { showAbout = true }, showDivider = false)
        }

        SecondaryButton(
            text = if (isDemoMode) "Вийти з гостьового режиму" else "Вийти",
            icon = Icons.AutoMirrored.Filled.Logout,
            onClick = { viewModel.signOut(onSignedOut) },
            modifier = Modifier.padding(top = 24.dp)
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Допомога") },
            text = {
                Column {
                    MenuRow(Icons.AutoMirrored.Outlined.MenuBook, "Як користуватися", {
                        showHelp = false
                        onOpenTutorial()
                    })
                    MenuRow(Icons.Outlined.Sos, "Запит допомоги волонтерам", {
                        showHelp = false
                        onOpenHelp()
                    }, showDivider = false)
                }
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Закрити") } }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("Мапа Інклюзивності") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Мобільний застосунок, який допомагає людям з інвалідністю та іншим маломобільним групам знаходити доступні місця, будувати зручні маршрути та досліджувати Шевченківський район без бар'єрів.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Джерела даних",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "• Мапа безбар'єрності: набір «Моніторинг та оцінка ступеня безбар'єрності об'єктів фізичного оточення для осіб з інвалідністю» (Мінрозвитку, ГО «ЛУН Місто»), data.gov.ua/dataset/38997a1f-2e86-4bd7-9054-cd9cd206d825. Ліцензія CC BY. Завантажено 24.10.2026.\n" +
                            "• Карта: OpenFreeMap, © OpenMapTiles, © OpenStreetMap contributors (ODbL); межа району, пошук адрес і маршрути — дані OpenStreetMap.\n" +
                            "• Перевірки за 7 критеріями й фото — від користувачів застосунку.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "Версія ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Закрити") } }
        )
    }
}

@Composable
private fun Avatar(name: String, imageUrl: String?, isGuest: Boolean) {
    val size = 64.dp
    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            val initial = name.trim().firstOrNull()?.uppercaseChar()
            if (isGuest || initial == null) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            } else {
                Text(text = initial.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, onClick: () -> Unit, showDivider: Boolean = true) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 16.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun GamificationBlock(points: Int, checksCount: Int) {
    MapaCard(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
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
