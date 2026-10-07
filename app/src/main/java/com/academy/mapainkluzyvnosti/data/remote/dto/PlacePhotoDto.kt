package com.academy.mapainkluzyvnosti.data.remote.dto

import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlacePhotoDto(
    val id: String? = null,
    @SerialName("place_id") val placeId: String,
    @SerialName("user_id") val userId: String,
    val url: String,
    @SerialName("ai_title") val aiTitle: String? = null,
    @SerialName("ai_suggestions") val aiSuggestions: List<String> = emptyList(),
    @SerialName("ai_suggested_status") val aiSuggestedStatus: AccessStatus? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AnalyzePlacePhotoRequest(val photoUrl: String)

@Serializable
data class AnalyzePlacePhotoResponse(
    val title: String,
    val suggestions: List<String>,
    val status: AccessStatus
)
