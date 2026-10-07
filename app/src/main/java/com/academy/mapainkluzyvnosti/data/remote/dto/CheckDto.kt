package com.academy.mapainkluzyvnosti.data.remote.dto

import com.academy.mapainkluzyvnosti.data.model.CheckResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CheckDto(
    val id: String? = null,
    @SerialName("place_id") val placeId: String,
    @SerialName("user_id") val userId: String,
    val ramp: Boolean,
    @SerialName("door_width") val doorWidth: Boolean,
    val threshold: Boolean,
    val elevator: Boolean,
    val toilet: Boolean,
    val tactile: Boolean,
    @SerialName("staff_assistance") val staffAssistance: Boolean,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

fun CheckDto.toDomainCheckResult() = CheckResult(
    ramp = ramp,
    doorWidth = doorWidth,
    threshold = threshold,
    elevator = elevator,
    toilet = toilet,
    tactile = tactile,
    staffAssistance = staffAssistance
)

fun CheckResult.toDto(placeId: String, userId: String, comment: String? = null) = CheckDto(
    placeId = placeId,
    userId = userId,
    ramp = ramp,
    doorWidth = doorWidth,
    threshold = threshold,
    elevator = elevator,
    toilet = toilet,
    tactile = tactile,
    staffAssistance = staffAssistance,
    comment = comment
)
