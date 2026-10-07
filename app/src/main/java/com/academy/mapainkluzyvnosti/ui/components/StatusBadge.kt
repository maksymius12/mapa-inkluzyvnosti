package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.model.AccessStatus

private fun Modifier.dashedBorder(color: androidx.compose.ui.graphics.Color, cornerRadiusDp: Dp) = drawBehind {
    val stroke = Stroke(
        width = 2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
    )
    drawRoundRect(
        color = color,
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(cornerRadiusDp.toPx()),
        style = stroke
    )
}

/** Маленький кружечок-індикатор статусу доступності (як бейдж на мітці мапи). */
@Composable
fun StatusDot(status: AccessStatus, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val color = status.color()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .let {
                if (status == AccessStatus.UNVERIFIED) {
                    it.dashedBorder(color, size / 2)
                } else {
                    it.background(color)
                }
            }
    )
}

/** Текстовий бейдж статусу — для карток місць та деталей. */
@Composable
fun StatusBadge(status: AccessStatus, modifier: Modifier = Modifier) {
    val color = status.color()
    val isUnverified = status == AccessStatus.UNVERIFIED
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .let {
                if (isUnverified) {
                    it.dashedBorder(color, 50.dp)
                } else {
                    it.background(color.copy(alpha = 0.16f))
                }
            }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}
