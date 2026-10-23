package com.academy.mapainkluzyvnosti.domain.usecase

import kotlin.math.max
import kotlin.math.min

/**
 * Правила полів користувацького місця. Дзеркалять серверні перевірки
 * (validate_user_place у міграції 20261023120000_user_places.sql).
 */
object PlaceInputRules {
    const val NAME_MIN = 3
    const val NAME_MAX = 80
    const val ADDRESS_MAX = 200
    const val REPORT_COMMENT_MAX = 500

    /** Радіус пошуку можливих дублів, метри. */
    const val DUPLICATE_RADIUS_METERS = 30.0

    private val whitespace = Regex("\\s+")
    private val controlChars = Regex("\\p{Cntrl}")

    /** Пробіли/переноси -> один пробіл, обрізання країв — так само, як робить сервер. */
    fun normalize(text: String): String = text.replace(whitespace, " ").trim()

    fun nameError(name: String): String? {
        val normalized = normalize(name)
        return when {
            normalized.isEmpty() -> "Вкажіть назву"
            normalized.length < NAME_MIN -> "Назва має містити щонайменше $NAME_MIN символи"
            normalized.length > NAME_MAX -> "Назва має містити не більше $NAME_MAX символів"
            controlChars.containsMatchIn(normalized) -> "Назва містить недопустимі символи"
            else -> null
        }
    }

    fun addressError(address: String): String? {
        val normalized = normalize(address)
        return when {
            normalized.length > ADDRESS_MAX -> "Адреса має містити не більше $ADDRESS_MAX символів"
            controlChars.containsMatchIn(normalized) -> "Адреса містить недопустимі символи"
            else -> null
        }
    }

    /**
     * «Та сама або дуже схожа назва»: збіг після нормалізації, одна назва містить іншу
     * або відстань Левенштейна не більше чверті довжини.
     */
    fun isSimilarName(a: String, b: String): Boolean {
        val x = comparable(a)
        val y = comparable(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        if (min(x.length, y.length) >= 4 && (x.contains(y) || y.contains(x))) return true
        val distance = levenshtein(x, y)
        return distance <= max(1, max(x.length, y.length) / 4)
    }

    private val punctuation = Regex("[\\p{P}\\p{S}]")

    private fun comparable(name: String): String =
        name.lowercase()
            .replace('ё', 'е')
            .replace('ʼ', '\'')
            .replace(punctuation, " ")
            .replace(whitespace, " ")
            .trim()

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
