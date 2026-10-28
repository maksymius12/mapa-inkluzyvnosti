package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavEntry(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * Нижня навігація: Мапа, Категорії, [SOS], Обране, Профіль. Активний пункт синій, решта приглушені.
 * [onSos] — запит допомоги як окремий червоний пункт по центру панелі (доступний з усіх вкладок).
 */
@Composable
fun MapaBottomNavBar(entries: List<BottomNavEntry>, modifier: Modifier = Modifier, onSos: (() -> Unit)? = null) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = androidx.compose.ui.unit.Dp.Hairline
        ) {
            entries.forEachIndexed { index, entry ->
                if (onSos != null && index == entries.size / 2) SosNavItem(onClick = onSos)
                NavigationBarItem(
                    selected = entry.selected,
                    onClick = entry.onClick,
                    icon = { Icon(entry.icon, contentDescription = entry.label) },
                    label = { Text(entry.label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }
        }
    }
}
