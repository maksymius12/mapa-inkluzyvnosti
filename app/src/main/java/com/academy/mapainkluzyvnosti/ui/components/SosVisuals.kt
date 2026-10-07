package com.academy.mapainkluzyvnosti.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.applyCanvas
import com.academy.mapainkluzyvnosti.data.model.SosProblemType
import com.academy.mapainkluzyvnosti.ui.theme.StatusPartial

fun SosProblemType.label(): String = when (this) {
    SosProblemType.OBSTACLE -> "Перешкода на шляху"
    SosProblemType.PHYSICAL_HELP -> "Потрібна фізична допомога"
    SosProblemType.OTHER -> "Інше"
}

/**
 * SOS-мітки навмисно мають іншу форму (трикутник з оклику), ніж кругла мітка
 * категорії місця — щоб волонтер не сплутав активний запит про допомогу
 * зі звичайною категорією на карті.
 */
fun renderSosMarkerDrawable(context: android.content.Context, sizePx: Int = 100): Drawable {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    bitmap.applyCanvas {
        val inset = sizePx * 0.08f
        val path = Path().apply {
            moveTo(sizePx / 2f, inset)
            lineTo(sizePx - inset, sizePx - inset)
            lineTo(inset, sizePx - inset)
            close()
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = StatusPartial.copy(alpha = 0.95f).toArgb()
            style = Paint.Style.FILL
        }
        drawPath(path, fillPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.045f
            strokeJoin = Paint.Join.ROUND
        }
        drawPath(path, strokePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.4f
            isFakeBoldText = true
        }
        val glyphCenterY = sizePx * 0.68f
        val textY = glyphCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
        drawText("!", sizePx / 2f, textY, textPaint)
    }
    return BitmapDrawable(context.resources, bitmap)
}
