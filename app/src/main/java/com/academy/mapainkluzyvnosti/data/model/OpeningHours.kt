package com.academy.mapainkluzyvnosti.data.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/** Години одного дня у хвилинах від півночі; [closeMin] < [openMin] — працює після півночі, 1440 = 24:00. */
data class DayHours(val openMin: Int, val closeMin: Int) {
    val text: String get() = "${formatMinutes(openMin)}–${formatMinutes(closeMin)}"
}

/**
 * Графік роботи (places.opening_hours, міграція 20261103120000). [days] — усі 7 днів; null — вихідний.
 * [always] — цілодобово (тоді [days] не використовується).
 */
data class OpeningHours(val always: Boolean, val days: Map<DayOfWeek, DayHours?>) {

    /** Відчинено в момент [now] (за замовчуванням — зараз за київським часом). */
    fun isOpen(now: ZonedDateTime = ZonedDateTime.now(KYIV)): Boolean {
        if (always) return true
        val minute = now.hour * 60 + now.minute
        val today = days[now.dayOfWeek]
        if (today != null) {
            if (today.closeMin > today.openMin && minute in today.openMin until today.closeMin) return true
            if (today.closeMin < today.openMin && minute >= today.openMin) return true
        }
        // Нічна зміна вчорашнього дня, що триває після півночі.
        val yesterday = days[now.dayOfWeek.minus(1)]
        return yesterday != null && yesterday.closeMin < yesterday.openMin && minute < yesterday.closeMin
    }

    fun toJson(): JsonElement = buildJsonObject {
        put("always", always)
        if (!always) {
            put("days", buildJsonObject {
                DayOfWeek.entries.forEach { day ->
                    val hours = days[day]
                    if (hours == null) {
                        put(day.key, JsonNull)
                    } else {
                        put(day.key, buildJsonObject {
                            put("open", formatMinutes(hours.openMin))
                            put("close", formatMinutes(hours.closeMin))
                        })
                    }
                }
            })
        }
    }

    companion object {
        val KYIV: ZoneId = ZoneId.of("Europe/Kyiv")

        fun parse(json: JsonElement?): OpeningHours? {
            val obj = json as? JsonObject ?: return null
            val always = obj["always"]?.jsonPrimitive?.booleanOrNull ?: return null
            if (always) return OpeningHours(true, emptyMap())
            val daysObj = obj["days"] as? JsonObject ?: return null
            val days = DayOfWeek.entries.associateWith { day ->
                val value = daysObj[day.key] as? JsonObject ?: return@associateWith null
                val open = value["open"]?.jsonPrimitive?.contentOrNull?.let(::parseMinutes)
                val close = value["close"]?.jsonPrimitive?.contentOrNull?.let(::parseMinutes)
                if (open != null && close != null) DayHours(open, close) else null
            }
            return OpeningHours(false, days)
        }
    }
}

val DayOfWeek.key: String get() = name.take(3).lowercase()

/** «09:00» → 540; «24:00» → 1440; інше — null. */
fun parseMinutes(text: String): Int? {
    val match = Regex("^([01]?\\d|2[0-4]):([0-5]\\d)$").find(text.trim()) ?: return null
    val (h, m) = match.destructured
    val minutes = h.toInt() * 60 + m.toInt()
    return minutes.takeIf { it <= 1440 }
}

fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
