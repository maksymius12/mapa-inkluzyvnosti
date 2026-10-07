package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory

@Composable
fun CategoryChip(
    category: PlaceCategory,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val visual = category.visual
    val background = if (selected) visual.color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) visual.color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = visual.label,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        Text(text = visual.label, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}
