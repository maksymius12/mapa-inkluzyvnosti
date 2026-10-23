package com.academy.mapainkluzyvnosti.ui.components

import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.applyCanvas
import com.academy.mapainkluzyvnosti.data.model.AccessibilityType
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.ui.theme.LegendNeutral
import com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier

/**
 * Растеризує мітку мапи за легендою: кольорове коло з білою обвідкою та значком типу
 * доступності (або літерою P). [type] = null — заклад без підтверджених типів (нейтральна мітка).
 */
fun renderTypeMarkerDrawable(
    context: android.content.Context,
    type: AccessibilityType?,
    sizePx: Int = 96
): Drawable {
    val visual = type?.visual
    return renderCircleMarker(
        context = context,
        sizePx = sizePx,
        fill = visual?.color ?: LegendNeutral,
        icon = if (type == null) Icons.Filled.Place else visual?.icon,
        glyph = visual?.glyph,
        glyphColor = visual?.glyphColor ?: Color.White
    )
}

/**
 * Мітка щойно доданого користувачем місця, яке ще не перевірили: колір і значок категорії,
 * пунктирна біла обвідка — як пунктирний бейдж «Не перевірено».
 */
fun renderUnverifiedCategoryMarkerDrawable(
    context: android.content.Context,
    category: PlaceCategory,
    sizePx: Int = 96
): Drawable {
    val visual = category.visual
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    bitmap.applyCanvas {
        val radius = sizePx / 2f
        val ringWidth = sizePx * 0.07f
        drawCircle(radius, radius, radius - ringWidth, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = visual.color.toArgb() })
        val dashedRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = ringWidth
            strokeCap = Paint.Cap.ROUND
            pathEffect = DashPathEffect(floatArrayOf(sizePx * 0.1f, sizePx * 0.08f), 0f)
        }
        drawCircle(radius, radius, radius - ringWidth / 2f, dashedRing)
        val iconSize = sizePx * 0.5f
        visual.icon.drawOnCanvas(this, (sizePx - iconSize) / 2f, (sizePx - iconSize) / 2f, iconSize, Color.White)
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
