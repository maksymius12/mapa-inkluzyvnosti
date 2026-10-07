package com.academy.mapainkluzyvnosti.ui.screens.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.ui.components.CategoryChip
import com.academy.mapainkluzyvnosti.ui.components.color
import com.academy.mapainkluzyvnosti.ui.components.label
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersScreen(onBack: () -> Unit, viewModel: FiltersViewModel = koinViewModel()) {
    val selection by viewModel.selection.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Фільтри") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::reset) { Text("Скинути") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(text = "Категорії", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlaceCategory.entries.forEach { category ->
                    CategoryChip(
                        category = category,
                        selected = category in selection.categories,
                        onClick = { viewModel.toggleCategory(category) }
                    )
                }
            }

            Text(text = "Статус доступності", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AccessStatus.entries.forEach { status ->
                    val selected = status in selection.statuses
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.toggleStatus(status) },
                        label = { Text(status.label()) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = status.color().copy(alpha = 0.18f),
                            selectedLabelColor = status.color()
                        )
                    )
                }
            }
        }
    }
}
