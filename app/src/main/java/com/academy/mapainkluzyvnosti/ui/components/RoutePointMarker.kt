package com.academy.mapainkluzyvnosti.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.applyCanvas

/** Проста кругла крапка для позначення початку/кінця маршруту на карті. */
fun renderRoutePointDrawable(context: android.content.Context, color: Color, sizePx: Int = 72): Drawable {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    bitmap.applyCanvas {
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val radius = sizePx / 2.6f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }
        drawCircle(cx, cy, radius, fillPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.09f
        }
        drawCircle(cx, cy, radius - ringPaint.strokeWidth / 2f, ringPaint)
    }
    return BitmapDrawable(context.resources, bitmap)
}
