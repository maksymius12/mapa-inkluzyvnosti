package com.academy.mapainkluzyvnosti.data.remote.dto

import com.academy.mapainkluzyvnosti.data.local.LocationSource
import com.academy.mapainkluzyvnosti.data.model.SosProblemType
import com.academy.mapainkluzyvnosti.data.model.SosRequest
import com.academy.mapainkluzyvnosti.data.model.SosStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SosRequestDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("problem_type") val problemType: SosProblemType? = null,
    val comment: String? = null,
    val status: SosStatus,
    @SerialName("resolved_by") val resolvedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("location_source") val locationSource: String? = null,
    @SerialName("location_accuracy_m") val locationAccuracyM: Float? = null,
    @SerialName("location_at") val locationAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toDomain() = SosRequest(
        id = id,
        userId = userId,
        lat = lat,
        lng = lng,
        problemType = problemType,
        comment = comment,
        status = status,
        createdAt = createdAt,
        locationSource = LocationSource.fromApi(locationSource),
        locationAccuracyM = locationAccuracyM,
        locationAt = locationAt,
        updatedAt = updatedAt
    )
}

/** Без значень за замовчуванням — щоб null-поля (немає локації) теж потрапили в JSON. */
@Serializable
data class SosRequestInsertDto(
    @SerialName("user_id") val userId: String,
    val lat: Double?,
    val lng: Double?,
    @SerialName("location_source") val locationSource: String,
    @SerialName("location_accuracy_m") val locationAccuracyM: Float?,
    @SerialName("location_at") val locationAt: String?,
    @SerialName("problem_type") val problemType: SosProblemType?,
    val comment: String?
)
