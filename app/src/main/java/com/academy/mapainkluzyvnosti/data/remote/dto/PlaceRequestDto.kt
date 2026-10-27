package com.academy.mapainkluzyvnosti.data.remote.dto

import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.data.model.PlaceRequestStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Заявка мешканця на нову локацію (таблиця place_requests). lat/lng тут немає:
 * на відміну від places, location у цій таблиці не має generated-колонок,
 * тож координати для списку не читаються назад (не потрібні — показ лише назви/адреси/статусу).
 */
@Serializable
data class PlaceRequestDto(
    val id: String,
    @SerialName("requester_id") val requesterId: String,
    val name: String,
    val address: String,
    val category: PlaceCategory,
    val note: String? = null,
    val status: PlaceRequestStatus,
    @SerialName("place_id") val placeId: String? = null,
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("reject_reason") val rejectReason: String? = null,
    @SerialName("created_at") val createdAt: String
)
