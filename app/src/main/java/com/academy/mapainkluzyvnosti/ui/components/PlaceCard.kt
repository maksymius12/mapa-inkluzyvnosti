package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.ui.theme.StatusPartial

/** Фото закладу або плейсхолдер за категорією (кольорова плитка зі значком категорії). */
@Composable
fun PlaceThumbnail(
    category: PlaceCategory,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    cornerRadius: Dp = 14.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    if (photoUrl != null) {
        AsyncImage(
            model = photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
        )
    } else {
        val visual = category.visual
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(visual.color.copy(alpha = 0.2f))
        ) {
            Icon(visual.icon, contentDescription = visual.label, tint = visual.color, modifier = Modifier.size(size * 0.46f))
        }
    }
}

/** Зірка + рейтинг (за потреби кількість відгуків). Нічого не показує, якщо рейтингу немає. */
@Composable
fun RatingRow(rating: Double?, reviewCount: Int? = null, modifier: Modifier = Modifier) {
    if (rating == null) return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = StatusPartial, modifier = Modifier.size(16.dp))
        Text(
            text = " ${formatRating(rating)}" + (reviewCount?.let { " (${reviewsLabel(it)})" } ?: ""),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** Картка закладу для списків: мініатюра, назва, рейтинг, категорія та статус доступності. */
@Composable
fun PlaceCard(
    place: Place,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    photoUrl: String? = null
) {
    MapaCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlaceThumbnail(category = place.category, photoUrl = photoUrl)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RatingRow(rating = place.rating)
                    Text(
                        text = place.category.visual.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                StatusBadge(status = place.status)
            }
        }
    }
}
