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
    /** null — фото без ШІ-аналізу (колонка порожня; список «[]» сервер не підставляє). */
    @SerialName("ai_suggestions") val aiSuggestions: List<String>? = null,
    @SerialName("ai_suggested_status") val aiSuggestedStatus: AccessStatus? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AnalyzePlacePhotoRequest(val photoUrl: String)

@Serializable
data class AnalyzePlacePhotoResponse(
    /** false — ШІ вважає, що на фото не реальне місце (мем, скриншот тощо): таке фото не зберігаємо. */
    val isPlacePhoto: Boolean = true,
    val title: String,
    /** Що видно на фото з погляду доступності (2–3 речення українською). */
    val summary: String = "",
    val suggestions: List<String>,
    val status: AccessStatus,
    /** Вердикт по кожному з 7 критеріїв: ключ — колонка checks (ramp, door_width, …). */
    val criteria: Map<String, CriterionVerdict> = emptyMap()
)

@Serializable
data class CriterionVerdict(
    /** yes / no / not_visible */
    val verdict: String,
    val reason: String = ""
)
