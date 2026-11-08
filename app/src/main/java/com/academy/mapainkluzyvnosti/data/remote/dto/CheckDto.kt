package com.academy.mapainkluzyvnosti.data.remote.dto

import com.academy.mapainkluzyvnosti.data.model.CheckResult
import com.academy.mapainkluzyvnosti.data.model.StopCheck
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Колонки checks, які клієнт може читати. user_id закрито для anon/authenticated (міграція 20261105120000),
 * тож `select=*` більше не працює — лише цей перелік. criteria — 9 критеріїв зупинки (міграція 20261105130000).
 */
const val CHECK_READ_COLUMNS =
    "id,place_id,ramp,door_width,threshold,elevator,toilet,tactile,staff_assistance,accessible_parking,comment,created_at,criteria"

/** Прочитана перевірка: вхід (7 колонок) або зупинка (criteria). Автора клієнт не бачить. */
@Serializable
data class CheckDto(
    val id: String? = null,
    @SerialName("place_id") val placeId: String,
    val ramp: Boolean? = null,
    @SerialName("door_width") val doorWidth: Boolean? = null,
    val threshold: Boolean? = null,
    val elevator: Boolean? = null,
    val toilet: Boolean? = null,
    val tactile: Boolean? = null,
    @SerialName("staff_assistance") val staffAssistance: Boolean? = null,
    @SerialName("accessible_parking") val accessibleParking: Boolean? = null,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val criteria: Map<String, Boolean>? = null
)

/** Перевірка входу; null — це перевірка зупинки або неповний рядок. */
fun CheckDto.toDomainCheckResult(): CheckResult? {
    if (criteria != null) return null
    return CheckResult(
        ramp = ramp ?: return null,
        doorWidth = doorWidth ?: return null,
        threshold = threshold ?: return null,
        elevator = elevator ?: return null,
        toilet = toilet ?: return null,
        tactile = tactile ?: return null,
        staffAssistance = staffAssistance ?: return null,
        accessibleParking = accessibleParking == true,
        checkedAt = createdAt
    )
}

fun CheckDto.toStopCheck(): StopCheck? = criteria?.let { StopCheck(answers = it, checkedAt = createdAt) }

/** Нова перевірка. user_id сервер звіряє з auth.uid() (RLS checks_insert_own). */
@Serializable
data class CheckInsertDto(
    @SerialName("place_id") val placeId: String,
    @SerialName("user_id") val userId: String,
    val ramp: Boolean? = null,
    @SerialName("door_width") val doorWidth: Boolean? = null,
    val threshold: Boolean? = null,
    val elevator: Boolean? = null,
    val toilet: Boolean? = null,
    val tactile: Boolean? = null,
    @SerialName("staff_assistance") val staffAssistance: Boolean? = null,
    @SerialName("accessible_parking") val accessibleParking: Boolean? = null,
    val comment: String? = null,
    val criteria: Map<String, Boolean>? = null
)

fun CheckResult.toDto(placeId: String, userId: String, comment: String? = null) = CheckInsertDto(
    placeId = placeId,
    userId = userId,
    ramp = ramp,
    doorWidth = doorWidth,
    threshold = threshold,
    elevator = elevator,
    toilet = toilet,
    tactile = tactile,
    staffAssistance = staffAssistance,
    accessibleParking = accessibleParking,
    comment = comment
)
