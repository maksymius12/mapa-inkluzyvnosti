package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.PLACE_SOURCE_USER
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.data.remote.dto.PlaceReadDto
import com.academy.mapainkluzyvnosti.data.remote.dto.PlaceReportInsertDto
import com.academy.mapainkluzyvnosti.data.remote.dto.PlaceReportReadDto
import com.academy.mapainkluzyvnosti.data.remote.dto.UserPlaceInsertDto
import com.academy.mapainkluzyvnosti.data.remote.dto.wktPoint
import com.academy.mapainkluzyvnosti.domain.usecase.PlaceInputRules
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Причина скарги на місце; [code] — значення колонки place_reports.reason. */
enum class PlaceReportReason(val code: String, val label: String) {
    DUPLICATE("duplicate", "Дубль"),
    WRONG_LOCATION("wrong_location", "Неправильне місце"),
    NOT_EXISTS("not_exists", "Не існує"),
    OTHER("other", "Інше")
}

/**
 * Місця, додані користувачами: створення, редагування, видалення й скарги.
 * Межі району, добовий ліміт, авторство й валідацію сервер перевіряє сам (тригери й RLS),
 * тут — лише запити.
 */
class UserPlaceRepository(
    private val client: SupabaseClient,
    private val placeRepository: PlaceRepository
) {

    suspend fun createPlace(
        authorId: String,
        name: String,
        category: PlaceCategory,
        address: String,
        lat: Double,
        lng: Double
    ): Place {
        val dto = UserPlaceInsertDto(
            name = PlaceInputRules.normalize(name),
            address = PlaceInputRules.normalize(address),
            category = category,
            location = wktPoint(lat, lng),
            source = PLACE_SOURCE_USER,
            createdBy = authorId
        )
        val created = client.postgrest.from("places")
            .insert(dto) { select() }
            .decodeSingle<PlaceReadDto>()
        return with(placeRepository) { created.toDomain(latestCheck = null) }
    }

    suspend fun updatePlace(placeId: String, name: String, category: PlaceCategory, address: String) {
        client.postgrest.from("places").update({
            set("name", PlaceInputRules.normalize(name))
            set("category", category)
            set("address", PlaceInputRules.normalize(address))
        }) { filter { eq("id", placeId) } }
    }

    /** Видалення разом із перевірками/фото/відгуками — через RPC, бо FK цих таблиць без каскаду. */
    suspend fun deletePlace(placeId: String) {
        client.postgrest.rpc("delete_own_place", buildJsonObject { put("p_place_id", placeId) })
    }

    suspend fun reportPlace(placeId: String, reporterId: String, reason: PlaceReportReason, comment: String?) {
        client.postgrest.from("place_reports").insert(
            PlaceReportInsertDto(
                placeId = placeId,
                reporterId = reporterId,
                reason = reason.code,
                comment = comment?.let(PlaceInputRules::normalize)?.takeIf { it.isNotEmpty() }
            )
        )
    }

    /** RLS віддає користувачу лише його власні скарги. */
    suspend fun hasReported(placeId: String): Boolean =
        client.postgrest.from("place_reports")
            .select { filter { eq("place_id", placeId) } }
            .decodeList<PlaceReportReadDto>()
            .isNotEmpty()

    /** Видимі місця в радіусі 30 м із такою самою або дуже схожою назвою. */
    suspend fun findSimilarNearby(name: String, lat: Double, lng: Double): List<Place> =
        placeRepository.placesNearby(lat, lng, PlaceInputRules.DUPLICATE_RADIUS_METERS)
            .filter { PlaceInputRules.isSimilarName(it.name, name) }
}
