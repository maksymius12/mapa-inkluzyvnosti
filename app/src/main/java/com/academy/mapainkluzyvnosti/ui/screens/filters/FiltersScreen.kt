package com.academy.mapainkluzyvnosti.ui.screens.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.AccessibilityType
import com.academy.mapainkluzyvnosti.ui.components.AccessibilityTypeBadge
import com.academy.mapainkluzyvnosti.ui.components.CategoryGroup
import com.academy.mapainkluzyvnosti.ui.components.IconBadge
import com.academy.mapainkluzyvnosti.ui.components.MapaSwitch
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import com.academy.mapainkluzyvnosti.ui.components.ScreenHeader
import com.academy.mapainkluzyvnosti.ui.components.visual
import org.koin.compose.viewmodel.koinViewModel

/** Екран 3: фільтри. Типи доступності та категорії місць; зміни одразу діють на мапу. */
@Composable
fun FiltersScreen(onBack: () -> Unit, viewModel: FiltersViewModel = koinViewModel()) {
    val selection by viewModel.selection.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Фільтри", onBack = onBack, onClose = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionTitle("Типи доступності")
            AccessibilityType.entries.forEach { type ->
                FilterRow(
                    title = type.visual.label,
                    checked = type in selection.types,
                    onCheckedChange = { viewModel.toggleType(type) },
                    badge = { AccessibilityTypeBadge(type = type, size = 36.dp, shape = RoundedCornerShape(10.dp)) }
                )
            }

            SectionTitle("Категорії місць", modifier = Modifier.padding(top = 20.dp))
            CategoryGroup.entries.forEach { group ->
                val visual = group.visual
                FilterRow(
                    title = group.label,
                    checked = selection.isGroupSelected(group),
                    onCheckedChange = { viewModel.toggleCategoryGroup(group) },
                    badge = { IconBadge(color = visual.color, icon = visual.icon, size = 36.dp, shape = RoundedCornerShape(10.dp)) }
                )
            }
        }

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
        ) {
            PrimaryButton(text = "Застосувати", onClick = onBack)
            TextButton(onClick = viewModel::reset, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Скинути фільтри", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.padding(top = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun FilterRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    badge: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
    ) {
        badge()
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        MapaSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
