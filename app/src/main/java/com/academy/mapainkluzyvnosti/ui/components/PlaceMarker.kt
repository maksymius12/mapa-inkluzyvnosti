package com.academy.mapainkluzyvnosti.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.applyCanvas
import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory

/** Кругла іконка категорії з бейджем статусу — для списків усередині Compose UI. */
@Composable
fun PlaceMarkerIcon(category: PlaceCategory, status: AccessStatus, modifier: Modifier = Modifier) {
    val visual = category.visual
    Box(modifier = modifier.size(40.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(visual.color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = visual.icon, contentDescription = visual.label, tint = visual.color)
        }
        StatusDot(status = status, modifier = Modifier.align(Alignment.TopEnd))
    }
}

/**
 * Растеризує мітку категорія+статус у [Drawable] для Symbol-маркера MapLibre.
 * MapLibre реєструє іконки як растрові зображення стилю — Compose ImageVector не можна
 * растеризувати поза композицією без ComposeView-хака, тож колір+бейдж статусу
 * лишаються головним індикатором на мапі; точна Material-іконка категорії
 * показується при відкритті картки/деталей місця (там уже звичайний Compose UI).
 */
fun renderPlaceMarkerDrawable(
    context: android.content.Context,
    category: PlaceCategory,
    status: AccessStatus,
    sizePx: Int = 96
): Drawable {
    val visual = category.visual
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    bitmap.applyCanvas {
        val radius = sizePx / 2f
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = visual.color.copy(alpha = 0.92f).toArgb()
        }
        drawCircle(radius, radius, radius, circlePaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.035f
        }
        drawCircle(radius, radius, radius - ringPaint.strokeWidth / 2f, ringPaint)

        val badgeRadius = sizePx * 0.16f
        val badgeCenterX = sizePx - badgeRadius - sizePx * 0.04f
        val badgeCenterY = badgeRadius + sizePx * 0.04f
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = status.color().toArgb()
        }
        if (status == AccessStatus.UNVERIFIED) {
            badgePaint.style = Paint.Style.STROKE
            badgePaint.strokeWidth = sizePx * 0.02f
            badgePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f)
        } else {
            badgePaint.style = Paint.Style.FILL
        }
        drawCircle(badgeCenterX, badgeCenterY, badgeRadius, badgePaint)

        val badgeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.015f
        }
        drawCircle(badgeCenterX, badgeCenterY, badgeRadius, badgeRingPaint)
    }
    return BitmapDrawable(context.resources, bitmap)
}
