package com.academy.mapainkluzyvnosti.data.model

/** Перевірка зупинки за 9 критеріями: відповіді за ключами (див. StopCriteria / criteria_definitions). */
data class StopCheck(
    val answers: Map<String, Boolean>,
    /** Коли перевірку збережено (ISO-8601 з бази). */
    val checkedAt: String? = null
)
