package com.academy.mapainkluzyvnosti.ui.screens.placedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.outlined.Accessible
import androidx.compose.material.icons.outlined.Elevator
import androidx.compose.material.icons.outlined.Hail
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.academy.mapainkluzyvnosti.data.model.AccessibilityType
import com.academy.mapainkluzyvnosti.data.model.CheckResult
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.ui.components.AccessibilityTypeBadge
import com.academy.mapainkluzyvnosti.ui.components.CategoryChip
import com.academy.mapainkluzyvnosti.ui.components.DemoGateDialog
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import com.academy.mapainkluzyvnosti.ui.components.RatingRow
import com.academy.mapainkluzyvnosti.ui.components.SecondaryButton
import com.academy.mapainkluzyvnosti.ui.components.StatusBadge
import com.academy.mapainkluzyvnosti.ui.components.visual
import com.academy.mapainkluzyvnosti.ui.theme.StatusAccessible
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val PhotoShape = RoundedCornerShape(20.dp)

/** Екран 4: деталі місця. Фото, назва, рейтинг, адреса, плитки доступності, додаткова інформація. */
@OptIn(ExperimentalLayoutApi::class)
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

    Column(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Box(modifier = Modifier.weight(1f).fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            place == null -> Column(
                modifier = Modifier.weight(1f).fillMaxWidth().statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                }
                Text(
                    text = "Місце не знайдено",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    PhotoHeader(
                        place = place,
                        photoUrl = uiState.latestPhoto?.url,
                        isFavorite = uiState.isFavorite,
                        onBack = onBack,
                        onToggleFavorite = viewModel::toggleFavorite
                    )

                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 16.dp)
                        )

                        FlowRow(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryChip(category = place.category, showIcon = false)
                            if (place.rating != null) {
                                RatingRow(rating = place.rating, reviewCount = place.reviewCount)
                            } else {
                                Text(
                                    text = "Ще немає відгуків",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(status = place.status)
                        }

                        Row(modifier = Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = place.address.ifBlank { "Адресу не вказано" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        AccessibilityTiles(place = place, modifier = Modifier.padding(top = 18.dp))

                        Text(
                            text = "Додаткова інформація",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
                        )
                        AdditionalInfo(place = place)

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 20.dp, bottom = 20.dp)
                        ) {
                            SecondaryButton(
                                text = "Пройти перевірку",
                                icon = Icons.Filled.PlaylistAddCheck,
                                onClick = onOpenQuickCheck
                            )
                            if (uiState.canAddPhoto) {
                                SecondaryButton(
                                    text = "Додати фото",
                                    icon = Icons.Filled.AddAPhoto,
                                    onClick = onOpenPhotoUpload
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    PrimaryButton(text = "Будувати маршрут", icon = Icons.Filled.Directions, onClick = onOpenRoute)
                }
            }
        }
    }
}

@Composable
private fun PhotoHeader(
    place: Place,
    photoUrl: String?,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .height(210.dp)
            .clip(PhotoShape)
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = place.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val visual = place.category.visual
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(visual.color.copy(alpha = 0.55f), visual.color.copy(alpha = 0.15f))))
            ) {
                Icon(visual.icon, contentDescription = visual.label, tint = Color.White, modifier = Modifier.size(72.dp))
            }
        }
        OverlayIconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
        }
        OverlayIconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Обране",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White
            )
        }
    }
}

@Composable
private fun OverlayIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        IconButton(onClick = onClick) { content() }
    }
}

/** Три плитки: пандус, туалет, паркування — кожна зі статусом у кольорі за легендою. */
@Composable
private fun AccessibilityTiles(place: Place, modifier: Modifier = Modifier) {
    val checks = place.checks
    val verified = checks != null
    val tiles = listOf(
        Triple(AccessibilityType.RAMP, checks?.ramp == true, "Є"),
        Triple(AccessibilityType.TOILET, checks?.toilet == true, "Доступний"),
        Triple(AccessibilityType.PARKING, place.hasAccessibleParking, "Є (для МГН)")
    )
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.forEach { (type, present, presentText) ->
            val visual = type.visual
            val statusText = when {
                present -> presentText
                verified -> "Немає"
                else -> "Не перевірено"
            }
            com.academy.mapainkluzyvnosti.ui.components.MapaCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp)
                ) {
                    AccessibilityTypeBadge(type = type, size = 40.dp, muted = !present)
                    Text(
                        text = visual.singularLabel,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (present) visual.color else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

private data class InfoItem(val icon: ImageVector, val title: String, val available: Boolean)

private fun infoItems(place: Place): List<InfoItem> {
    val checks: CheckResult? = place.checks
    return listOf(
        InfoItem(Icons.Outlined.Accessible, "Безбар'єрний вхід", checks != null && checks.ramp && checks.doorWidth && checks.threshold),
        InfoItem(Icons.Outlined.Elevator, "Ліфт", checks?.elevator == true),
        InfoItem(Icons.Outlined.LocalParking, "Спеціальні місця для паркування", place.hasAccessibleParking),
        InfoItem(Icons.Outlined.Hail, "Допомога персоналу", checks?.staffAssistance == true),
        InfoItem(Icons.Outlined.TouchApp, "Тактильні позначки", checks?.tactile == true)
    )
}

/** Список за даними останньої перевірки: наявне — зелена галочка, відсутнє чи неперевірене — приглушено. */
@Composable
private fun AdditionalInfo(place: Place) {
    val items = infoItems(place)
    Column {
        items.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = if (item.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 14.dp)
                )
                if (item.available) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Є", tint = StatusAccessible, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        text = if (place.checks == null) "Не перевірено" else "Немає",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (index < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
