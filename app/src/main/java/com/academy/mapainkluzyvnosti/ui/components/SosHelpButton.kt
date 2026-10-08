package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.ui.theme.StatusPartial

/**
 * Навмисно не яскраво-червона — щоб не сприймалась як екстрений виклик 101/112.
 * Це запит про допомогу волонтерам поруч, а не служба порятунку.
 */
@Composable
fun SosHelpButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, StatusPartial), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Icon(Icons.Filled.PriorityHigh, contentDescription = null, tint = StatusPartial, modifier = Modifier.size(18.dp))
            Text(
                text = "Потрібна допомога",
                style = MaterialTheme.typography.labelLarge,
                color = StatusPartial,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Text(
            text = "Для волонтерів поруч, не екстрена служба",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
