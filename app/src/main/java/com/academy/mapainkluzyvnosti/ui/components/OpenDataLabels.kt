package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.OPEN_DATA_ATTRIBUTION
import com.academy.mapainkluzyvnosti.data.model.Place

/** Маленька позначка, що відрізняє імпортовані точки від доданих користувачами. */
@Composable
fun OpenDataTag(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        text = "з відкритих даних",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/**
 * Що відомо з відкритих даних (лише явні критерії набору) і підпис джерела — вимога ліцензії CC BY.
 * Показується на картці місця з відкритих даних.
 */
@Composable
fun OpenDataSourceNote(place: Place, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        if (place.verifiedNote.isNotBlank()) {
            Text(
                text = place.verifiedNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = OPEN_DATA_ATTRIBUTION,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
