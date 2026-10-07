package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.ui.theme.StatusPartial

/**
 * Навмисно не яскраво-червона — щоб не сприймалась як екстрений виклик 101/112.
 * Це запит про допомогу волонтерам поруч, а не служба порятунку.
 */
@Composable
fun SosHelpButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusPartial),
            border = BorderStroke(1.dp, StatusPartial)
        ) {
            Icon(Icons.Filled.PriorityHigh, contentDescription = null, tint = StatusPartial)
            Text(" Потрібна допомога", modifier = Modifier.padding(start = 4.dp))
        }
        Text(
            text = "Повідомлення волонтерам поруч, не екстрена служба",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
