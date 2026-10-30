package com.academy.mapainkluzyvnosti.ui.screens.placedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.PlaceReview
import com.academy.mapainkluzyvnosti.ui.components.SecondaryButton
import com.academy.mapainkluzyvnosti.ui.components.formatCheckDate
import com.academy.mapainkluzyvnosti.ui.components.formatRating
import com.academy.mapainkluzyvnosti.ui.components.reviewsLabel
import com.academy.mapainkluzyvnosti.ui.theme.StatusPartial

private const val REVIEW_COMMENT_MAX = 1000

/** П'ять зірок за рейтингом (з половинками), як у Google Maps. */
@Composable
fun StarRating(rating: Double, modifier: Modifier = Modifier, starSize: Dp = 18.dp) {
    Row(modifier = modifier.semantics { contentDescription = "Рейтинг ${formatRating(rating)} з 5" }) {
        for (i in 1..5) {
            val icon = when {
                rating >= i - 0.25 -> Icons.Filled.Star
                rating >= i - 0.75 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(icon, contentDescription = null, tint = StatusPartial, modifier = Modifier.size(starSize))
        }
    }
}

/** Біля назви місця: зірки, середня оцінка й кількість відгуків. */
@Composable
fun ReviewSummary(rating: Double?, reviewCount: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (rating != null && reviewCount > 0) {
            Text(text = formatRating(rating), style = MaterialTheme.typography.labelLarge)
            StarRating(rating = rating, modifier = Modifier.padding(horizontal = 6.dp))
            Text(
                text = "(${reviewsLabel(reviewCount)})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "Ще немає відгуків",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Розділ «Відгуки»: кнопка залишити/редагувати (лише залогіненим) і список відгуків. */
@Composable
fun ReviewsSection(
    reviews: List<PlaceReview>,
    canWriteReview: Boolean,
    hasOwnReview: Boolean,
    onWriteReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = "Відгуки", style = MaterialTheme.typography.titleMedium)
        if (canWriteReview) {
            SecondaryButton(
                text = if (hasOwnReview) "Редагувати свій відгук" else "Залишити відгук",
                icon = Icons.Filled.RateReview,
                onClick = onWriteReview,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (reviews.isEmpty()) {
            Text(
                text = "Поки ніхто не залишив відгук",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                reviews.forEachIndexed { index, review ->
                    ReviewItem(review)
                    if (index < reviews.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ReviewItem(review: PlaceReview) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = review.authorName, style = MaterialTheme.typography.titleSmall)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            StarRating(rating = review.rating.toDouble(), starSize = 16.dp)
            formatCheckDate(review.createdAt)?.let { date ->
                Text(
                    text = if (review.updatedAt != review.createdAt) "$date (змінено)" else date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        review.comment?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Форма відгуку: зірки 1–5 і текст. Для власного відгуку відкривається з його поточними даними. */
@Composable
fun ReviewDialog(
    existing: PlaceReview?,
    isSaving: Boolean,
    error: String?,
    onSubmit: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var rating by rememberSaveable { mutableIntStateOf(existing?.rating ?: 0) }
    var comment by rememberSaveable { mutableStateOf(existing?.comment.orEmpty()) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (existing != null) "Редагувати відгук" else "Залишити відгук") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = "Ваша оцінка", style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    for (i in 1..5) {
                        IconButton(onClick = { rating = i }, enabled = !isSaving) {
                            Icon(
                                imageVector = if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "$i з 5",
                                tint = StatusPartial,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(REVIEW_COMMENT_MAX) },
                    label = { Text("Коментар (необов'язково)") },
                    enabled = !isSaving,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(rating, comment) }, enabled = rating in 1..5 && !isSaving) {
                Text(if (isSaving) "Зберігаємо…" else "Зберегти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Скасувати") }
        }
    )
}
