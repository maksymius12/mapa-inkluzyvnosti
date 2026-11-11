package com.academy.mapainkluzyvnosti.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.applyCanvas
import com.academy.mapainkluzyvnosti.data.local.LocationSource
import com.academy.mapainkluzyvnosti.data.model.SosProblemType
import com.academy.mapainkluzyvnosti.data.model.SosRequest
import com.academy.mapainkluzyvnosti.ui.theme.StatusPartial

fun SosProblemType.label(): String = when (this) {
    SosProblemType.CANT_ENTER -> "Не можу зайти / сходи"
    SosProblemType.STUCK -> "Застряг"
    SosProblemType.ESCORT -> "Потрібен супровід"
    SosProblemType.OTHER -> "Інше"
    SosProblemType.OBSTACLE -> "Перешкода на шляху"
    SosProblemType.PHYSICAL_HELP -> "Потрібна фізична допомога"
}

/** Швидкі варіанти після відправки SOS. */
val SosQuickProblems = listOf(SosProblemType.CANT_ENTER, SosProblemType.STUCK, SosProblemType.ESCORT, SosProblemType.OTHER)

/** Заголовок запиту для волонтера: тип, якщо вже вказано. */
fun SosProblemType?.titleOrDefault(): String = this?.label() ?: "Потрібна допомога (деталі ще не вказано)"

/**
 * Наскільки вірити локації запиту — для волонтера: «Точна геолокація, ±20 м», «Локація з мережі, ±300 м»,
 * «Остання відома 7 хв тому», «Вказано вручну», «Локація невідома».
 */
fun sosLocationLabel(source: LocationSource, accuracyM: Float?, locationAtMs: Long?, nowMs: Long = System.currentTimeMillis()): String {
    val accuracy = accuracyM?.let { ", ±${roundAccuracy(it)} м" }.orEmpty()
    return when (source) {
        LocationSource.GPS -> "Точна геолокація$accuracy"
        LocationSource.NETWORK -> "Локація з мережі$accuracy"
        LocationSource.LAST_KNOWN -> {
            val minutes = locationAtMs?.let { (nowMs - it) / 60_000 }
            if (minutes != null) "Остання відома ${agoText(minutes)}$accuracy" else "Остання відома позиція$accuracy"
        }
        LocationSource.MANUAL -> "Вказано вручну"
        LocationSource.NONE -> "Локація невідома"
    }
}

/** Те саме для запиту з сервера (час локації — ISO-рядок). */
fun SosRequest.locationLabel(nowMs: Long = System.currentTimeMillis()): String =
    sosLocationLabel(locationSource, locationAccuracyM, locationAt?.let(::parseInstantMs), nowMs)

private fun roundAccuracy(meters: Float): Int = when {
    meters < 100 -> ((meters / 5).toInt() * 5).coerceAtLeast(5)
    else -> (meters / 50).toInt() * 50
}

private fun agoText(minutes: Long): String = when {
    minutes < 1 -> "щойно"
    minutes < 60 -> "$minutes хв тому"
    minutes < 24 * 60 -> "${minutes / 60} год тому"
    else -> "${minutes / (24 * 60)} дн тому"
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
