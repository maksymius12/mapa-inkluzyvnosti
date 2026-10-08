package com.academy.mapainkluzyvnosti.ui.screens.favorites

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.academy.mapainkluzyvnosti.ui.components.IconBadge
import com.academy.mapainkluzyvnosti.ui.components.MapaCard
import com.academy.mapainkluzyvnosti.ui.components.PlaceCard
import com.academy.mapainkluzyvnosti.ui.components.ScreenHeader
import com.academy.mapainkluzyvnosti.ui.components.formatDistanceKm
import com.academy.mapainkluzyvnosti.ui.components.formatDurationMinutes
import com.academy.mapainkluzyvnosti.ui.navigation.Routes
import org.koin.compose.viewmodel.koinViewModel

/** Екран 6: обране. Вкладки «Місця» і «Маршрути». */
@Composable
fun FavoritesScreen(
    initialTab: String,
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    viewModel: FavoritesViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable(initialTab) {
        mutableIntStateOf(if (initialTab == Routes.FAVORITES_TAB_ROUTES) 1 else 0)
    }

    // Список місць оновлюємо при кожному поверненні на екран — обране могли змінити в картці закладу.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.loadFavoritePlaces()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Обране", onBack = onBack)
        FavoritesTabs(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (uiState.isLoading && selectedTab == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        when (selectedTab) {
            0 -> {
                if (uiState.places.isEmpty()) {
                    EmptyState("Ще немає обраних місць")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.places, key = { it.id }) { place ->
                            PlaceCard(
                                place = place,
                                photoUrl = uiState.photoUrls[place.id],
                                onClick = { onOpenPlace(place.id) }
                            )
                        }
                    }
                }
            }
            else -> {
                if (uiState.routes.isEmpty()) {
                    EmptyState("Ще немає збережених маршрутів")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.routes, key = { it.id }) { route ->
                            MapaCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
                                ) {
                                    IconBadge(
                                        color = MaterialTheme.colorScheme.primary,
                                        icon = Icons.Filled.Route,
                                        size = 44.dp
                                    )
                                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(
                                            text = "${route.fromName} → ${route.toName}",
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 2
                                        )
                                        Text(
                                            text = "${formatDistanceKm(route.distanceMeters)} · ${formatDurationMinutes(route.durationSeconds)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removeRoute(route) }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Видалити маршрут",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Перемикач вкладок у вигляді капсули: активна вкладка підсвічена синім. */
@Composable
private fun FavoritesTabs(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(4.dp)
    ) {
        listOf("Місця", "Маршрути").forEachIndexed { index, title ->
            val active = index == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
