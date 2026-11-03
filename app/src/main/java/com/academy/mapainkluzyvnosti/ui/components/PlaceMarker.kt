package com.academy.mapainkluzyvnosti.ui.components

import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.applyCanvas
import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier

/**
 * Мітка місця на мапі (як у README): сама мітка — колір і значок КАТЕГОРІЇ закладу, маленький значок у куті —
 * СТАТУС доступності: зелений (доступно), жовтий (частково), червоний (бар'єр), пунктирний (не перевірено).
 * Раніше після першої перевірки мітка підмінялася значком критерію чи активного фільтра (візок, туалет…),
 * і категорія зникала.
 */
fun renderPlaceMarkerDrawable(
    context: android.content.Context,
    category: PlaceCategory,
    status: AccessStatus,
    sizePx: Int = 104
): Drawable {
    val visual = category.visual
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    bitmap.applyCanvas {
        // Основне коло трохи зміщене вліво-вгору, щоб бейдж статусу помістився в правому нижньому куті.
        val radius = sizePx * 0.42f
        val cx = sizePx * 0.44f
        val cy = sizePx * 0.44f
        val ring = sizePx * 0.055f
        drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
        drawCircle(cx, cy, radius - ring, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = visual.color.toArgb() })
        val iconSize = radius * 1.15f
        visual.icon.drawOnCanvas(this, cx - iconSize / 2f, cy - iconSize / 2f, iconSize, Color.White)

        val badgeRadius = sizePx * 0.17f
        val bx = sizePx - badgeRadius - sizePx * 0.03f
        val by = sizePx - badgeRadius - sizePx * 0.03f
        val badgeRing = sizePx * 0.04f
        if (status == AccessStatus.UNVERIFIED) {
            // «Не перевірено» — пунктирне кільце на темному тлі, як пунктирний бейдж у картці.
            drawCircle(bx, by, badgeRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B2333.toInt() })
            drawCircle(bx, by, badgeRadius - badgeRing / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = badgeRing
                pathEffect = DashPathEffect(floatArrayOf(sizePx * 0.045f, sizePx * 0.035f), 0f)
            })
        } else {
            drawCircle(bx, by, badgeRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
            drawCircle(bx, by, badgeRadius - badgeRing, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = status.color().toArgb() })
        }
    }
    return BitmapDrawable(context.resources, bitmap)
}

/** Червона мітка бар'єра на маршруті. */
fun renderBarrierMarkerDrawable(context: android.content.Context, sizePx: Int = 96): Drawable =
    renderCircleMarker(context, sizePx, StatusBarrier, Icons.Filled.Warning, null, Color.White)

private fun renderCircleMarker(
    context: android.content.Context,
    sizePx: Int,
    fill: Color,
    icon: ImageVector?,
    glyph: String?,
    glyphColor: Color
): Drawable {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    bitmap.applyCanvas {
        val radius = sizePx / 2f
        val ringWidth = sizePx * 0.06f
        drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
        drawCircle(radius, radius, radius - ringWidth, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill.toArgb() })

        if (icon != null) {
            val iconSize = sizePx * 0.58f
            icon.drawOnCanvas(this, (sizePx - iconSize) / 2f, (sizePx - iconSize) / 2f, iconSize, glyphColor)
        } else if (glyph != null) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = glyphColor.toArgb()
                textAlign = Paint.Align.CENTER
                textSize = sizePx * 0.56f
                isFakeBoldText = true
            }
            val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2f
            drawText(glyph, radius, textY, textPaint)
        }
    }
    return BitmapDrawable(context.resources, bitmap)
}
