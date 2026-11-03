package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Червоний бейдж із кількістю того, що чекає розгляду; 0 — нічого не показує. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Badge(
        containerColor = MaterialTheme.colorScheme.error,
        modifier = modifier.semantics { contentDescription = "$count очікують розгляду" }
    ) {
        Text(if (count > 99) "99+" else count.toString())
    }
}
