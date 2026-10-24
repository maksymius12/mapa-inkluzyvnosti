package com.academy.mapainkluzyvnosti.data.repository

import android.util.Log
import com.academy.mapainkluzyvnosti.data.model.PLACE_SOURCE_USER
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.data.remote.dto.CreatedPlaceDto
import com.academy.mapainkluzyvnosti.data.remote.dto.CreatedPlaceIdDto
import com.academy.mapainkluzyvnosti.data.remote.dto.PlaceReportInsertDto
import com.academy.mapainkluzyvnosti.data.remote.dto.PlaceReportReadDto
import com.academy.mapainkluzyvnosti.data.remote.dto.UserPlaceInsertDto
import com.academy.mapainkluzyvnosti.data.remote.dto.wktPoint
import com.academy.mapainkluzyvnosti.domain.usecase.PlaceInputRules
import com.academy.mapainkluzyvnosti.data.remote.NothingUpdatedException
import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
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
private const val TAG = "UserPlaceRepository"

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
        // Тригер places_before_insert вимагає created_by = auth.uid() — беремо id саме з сесії.
        val sessionUserId = client.auth.currentUserOrNull()?.id ?: authorId
        val dto = UserPlaceInsertDto(
            name = PlaceInputRules.normalize(name),
            address = PlaceInputRules.normalize(address),
            category = category,
            location = wktPoint(lat, lng),
            source = PLACE_SOURCE_USER,
            createdBy = sessionUserId
        )
        val created = try {
            client.postgrest.from("places")
                .insert(dto) { select(Columns.list("id", "name", "address", "category", "status", "source", "created_by")) }
                .decodeSingle<CreatedPlaceDto>()
        } catch (e: PostgrestRestException) {
            // Справжня причина відмови: коди MPxxx — перевірки тригера (межі району, ліміт, поля),
            // 42501 — RLS, 22P02/23xxx — тип або обмеження колонки, PGRST204 — немає такої колонки.
            Log.e(
                TAG,
                "POST /rest/v1/places -> HTTP ${e.statusCode} code=${e.code} message=${e.error} " +
                    "details=${e.details} hint=${e.hint}; insert fields: ${dto.logFields(authorId)}"
            )
            throw e
        }
        return Place(
            id = created.id,
            name = created.name,
            address = created.address.orEmpty(),
            category = created.category,
            lat = lat,
            lng = lng,
            status = created.status,
            checks = null,
            source = created.source,
            verifiedNote = "",
            createdBy = created.createdBy
        )
    }

    /** Поля INSERT для логу: без тексту назви/адреси, лише довжини. */
    private fun UserPlaceInsertDto.logFields(storeUserId: String) =
        "name(len=${name.length}) address(len=${address.length}) category=$category location=$location " +
            "source=$source created_by_matches_session=${createdBy == client.auth.currentUserOrNull()?.id} " +
            "store_user_matches_session=${storeUserId == createdBy}"

    /**
     * Зміна назви/категорії/адреси власного місця. RLS і тригер places_before_update дозволяють це лише
     * автору; якщо PATCH не зачепив жодного рядка (немає прав, місце приховане чи видалене) —
     * NothingUpdatedException, а не тихе «збережено».
     */
    suspend fun updatePlace(placeId: String, name: String, category: PlaceCategory, address: String) {
        val fields = mapOf(
            "id" to placeId,
            "name_length" to PlaceInputRules.normalize(name).length,
            "category" to category,
            "address_length" to PlaceInputRules.normalize(address).length
        )
        val updated = try {
            client.postgrest.from("places").update({
                set("name", PlaceInputRules.normalize(name))
                set("category", category)
                set("address", PlaceInputRules.normalize(address))
            }) {
                select(Columns.list("id"))
                filter { eq("id", placeId) }
            }.decodeList<CreatedPlaceIdDto>()
        } catch (e: Exception) {
            SupabaseErrorLog.log("PATCH /rest/v1/places", e, fields)
            throw e
        }
        if (updated.isEmpty()) {
            val e = NothingUpdatedException("PATCH /rest/v1/places")
            SupabaseErrorLog.log("PATCH /rest/v1/places", e, fields)
            throw e
        }
    }

    /** Видалення разом із перевірками/фото/відгуками — через RPC, бо FK цих таблиць без каскаду. */
    suspend fun deletePlace(placeId: String) {
        try {
            client.postgrest.rpc("delete_own_place", buildJsonObject { put("p_place_id", placeId) })
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/rpc/delete_own_place", e, mapOf("p_place_id" to placeId))
            throw e
        }
    }

    suspend fun reportPlace(placeId: String, reporterId: String, reason: PlaceReportReason, comment: String?) {
        try {
            client.postgrest.from("place_reports").insert(
                PlaceReportInsertDto(
                    placeId = placeId,
                    reporterId = reporterId,
                    reason = reason.code,
                    comment = comment?.let(PlaceInputRules::normalize)?.takeIf { it.isNotEmpty() }
                )
            )
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/place_reports", e, mapOf("place_id" to placeId, "reason" to reason.code))
            throw e
        }
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
