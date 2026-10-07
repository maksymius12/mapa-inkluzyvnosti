package com.academy.mapainkluzyvnosti.ui.screens.placedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.academy.mapainkluzyvnosti.data.model.CheckResult
import com.academy.mapainkluzyvnosti.ui.components.CategoryChip
import com.academy.mapainkluzyvnosti.ui.components.DemoGateDialog
import com.academy.mapainkluzyvnosti.ui.components.PlaceMarkerIcon
import com.academy.mapainkluzyvnosti.ui.components.StatusBadge
import com.academy.mapainkluzyvnosti.ui.theme.StatusPartial
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private data class CheckCriterion(val label: String, val value: Boolean)

private fun CheckResult.toCriteria(): List<CheckCriterion> = listOf(
    CheckCriterion("Пандус", ramp),
    CheckCriterion("Ширина дверей", doorWidth),
    CheckCriterion("Без порогів", threshold),
    CheckCriterion("Ліфт", elevator),
    CheckCriterion("Туалет", toilet),
    CheckCriterion("Тактильні позначки", tactile),
    CheckCriterion("Допомога персоналу", staffAssistance)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
    placeId: String,
    onBack: () -> Unit,
    onOpenQuickCheck: () -> Unit,
    onOpenPhotoUpload: () -> Unit,
    onOpenRoute: () -> Unit,
    onRequestLogin: () -> Unit,
    viewModel: PlaceDetailViewModel = koinViewModel(parameters = { parametersOf(placeId) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val place = uiState.place

    if (uiState.showDemoGate) {
        DemoGateDialog(onDismiss = viewModel::dismissDemoGate, onSignInWithGoogle = onRequestLogin)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(place?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (uiState.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Обране",
                            tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (place == null) {
                Text(
                    text = "Місце не знайдено",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    val photoUrl = uiState.latestPhoto?.url
                    if (photoUrl != null) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = place.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            PlaceMarkerIcon(category = place.category, status = place.status, modifier = Modifier.size(64.dp))
                        }
                    }

                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = place.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryChip(category = place.category)
                        StatusBadge(status = place.status)
                    }

                    place.rating?.let { rating ->
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = StatusPartial, modifier = Modifier.size(18.dp))
                            Text(
                                text = " %.1f (%d)".format(rating, place.reviewCount),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Text(
                        text = "Доступність",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                    val checks = place.checks
                    if (checks == null) {
                        StatusBadge(status = com.academy.mapainkluzyvnosti.data.model.AccessStatus.UNVERIFIED)
                    } else {
                        Column {
                            checks.toCriteria().forEach { criterion ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (criterion.value) Icons.Filled.Check else Icons.Filled.Close,
                                        contentDescription = null,
                                        tint = if (criterion.value) {
                                            com.academy.mapainkluzyvnosti.ui.theme.StatusAccessible
                                        } else {
                                            com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(text = criterion.label, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(top = 24.dp)) {
                        Button(onClick = onOpenRoute, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Directions, contentDescription = null)
                            Text(" Побудувати маршрут", modifier = Modifier.padding(start = 4.dp))
                        }
                        OutlinedButton(
                            onClick = onOpenQuickCheck,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Icon(Icons.Filled.PlaylistAddCheck, contentDescription = null)
                            Text(" Пройти перевірку", modifier = Modifier.padding(start = 4.dp))
                        }
                        if (uiState.canAddPhoto) {
                            OutlinedButton(
                                onClick = onOpenPhotoUpload,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            ) {
                                Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                                Text(" Додати фото", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
