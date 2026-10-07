package com.academy.mapainkluzyvnosti.data.remote.dto

import com.academy.mapainkluzyvnosti.data.model.SosProblemType
import com.academy.mapainkluzyvnosti.data.model.SosRequest
import com.academy.mapainkluzyvnosti.data.model.SosStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SosRequestDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val lat: Double,
    val lng: Double,
    @SerialName("problem_type") val problemType: SosProblemType,
    val comment: String? = null,
    val status: SosStatus,
    @SerialName("resolved_by") val resolvedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null
) {
    fun toDomain() = SosRequest(
        id = id,
        userId = userId,
        lat = lat,
        lng = lng,
        problemType = problemType,
        comment = comment,
        status = status,
        createdAt = createdAt
    )
}

@Serializable
data class SosRequestInsertDto(
    @SerialName("user_id") val userId: String,
    val lat: Double,
    val lng: Double,
    @SerialName("problem_type") val problemType: SosProblemType,
    val comment: String? = null
)
