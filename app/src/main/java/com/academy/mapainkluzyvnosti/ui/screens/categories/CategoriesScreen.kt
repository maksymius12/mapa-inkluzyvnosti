package com.academy.mapainkluzyvnosti.ui.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.ui.components.CategoryGroup
import com.academy.mapainkluzyvnosti.ui.components.IconBadge
import com.academy.mapainkluzyvnosti.ui.components.MapaCard
import com.academy.mapainkluzyvnosti.ui.components.SecondaryButton
import com.academy.mapainkluzyvnosti.ui.screens.filters.FiltersViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Вкладка «Категорії»: обрати групу місць — мапа покаже лише її. */
@Composable
fun CategoriesScreen(onCategoryChosen: () -> Unit, viewModel: FiltersViewModel = koinViewModel()) {
    val selection by viewModel.selection.collectAsState()
    val allSelected = CategoryGroup.entries.all { selection.isGroupSelected(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(text = "Категорії", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Оберіть тип місць, які показати на мапі",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        CategoryGroup.entries.chunked(2).forEach { rowGroups ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                rowGroups.forEach { group ->
                    val visual = group.visual
                    val active = !allSelected && selection.isGroupSelected(group) &&
                        CategoryGroup.entries.count { selection.isGroupSelected(it) } == 1
                    MapaCard(
                        onClick = {
                            viewModel.showOnlyCategoryGroup(group)
                            onCategoryChosen()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconBadge(color = visual.color, icon = visual.icon, size = 48.dp)
                            Text(
                                text = group.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (rowGroups.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }

        SecondaryButton(
            text = "Показати всі категорії",
            onClick = {
                viewModel.showAllCategories()
                onCategoryChosen()
            },
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
