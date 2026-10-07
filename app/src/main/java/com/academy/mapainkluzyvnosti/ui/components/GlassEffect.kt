package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Liquid Glass: напівпрозора розмита поверхня (Haze, "frosted glass" на вмісті під
 * [hazeState]) + тонка світла обвідка країв. Використовується для плаваючих елементів
 * над картою (кнопки, нижня навігація) — не для карток з детальним контентом, де
 * прозорість заважала б читабельності.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
fun Modifier.liquidGlass(hazeState: HazeState, shape: Shape = CircleShape): Modifier = composed {
    val style = HazeMaterials.ultraThin(containerColor = MaterialTheme.colorScheme.surface)
    this
        .clip(shape)
        .hazeEffect(hazeState, style = style)
        .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
}
