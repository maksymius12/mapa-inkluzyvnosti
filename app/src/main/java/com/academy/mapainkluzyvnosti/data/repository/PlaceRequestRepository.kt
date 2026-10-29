package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.data.model.PlaceRequest
import com.academy.mapainkluzyvnosti.data.model.PlaceRequestStatus
import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import com.academy.mapainkluzyvnosti.data.remote.dto.PlaceRequestDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Заявки мешканців на нові локації: подача (мешканець), черга на розгляд і рішення
 * (волонтер/учень/адмін — RLS і RPC на сервері перевіряють права самі).
 */
private val rowJson = Json { ignoreUnknownKeys = true }

private const val REVIEW_PHOTOS_BUCKET = "review-photos"

class PlaceRequestRepository(private val client: SupabaseClient) {

    /** Подати заявку — лише для мешканців (інші додають місце напряму через UserPlaceRepository). */
    suspend fun submit(name: String, address: String, category: PlaceCategory, lat: Double, lng: Double, note: String?): PlaceRequest {
        val params = mapOf(
            "p_name" to name,
            "p_address" to address,
            "p_category" to category,
            "p_lat" to lat,
            "p_lng" to lng
        )
        return try {
            client.postgrest.rpc(
                "submit_place_request",
                buildJsonObject {
                    put("p_name", name)
                    put("p_address", address)
                    put("p_category", categoryApiValue(category))
                    put("p_lat", lat)
                    put("p_lng", lng)
                    note?.takeIf { it.isNotBlank() }?.let { put("p_note", it) }
                }
            ).decodeAs<JsonElement>().let(::singleRow).toDomain()
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/rpc/submit_place_request", e, params)
            throw e
        }
    }

    /** Власні заявки мешканця — з їхнім статусом (очікує/прийнято/відхилено). */
    suspend fun myRequests(): List<PlaceRequest> =
        client.postgrest.from("place_requests")
            .select { order("created_at", Order.DESCENDING) }
            .decodeList<PlaceRequestDto>()
            .map { it.toDomain() }

    /** Усі заявки, що очікують розгляду — видимі кожному рецензенту (RLS: is_reviewer()). */
    suspend fun pendingForReview(): List<PlaceRequest> =
        client.postgrest.from("place_requests")
            .select {
                filter { eq("status", "pending") }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<PlaceRequestDto>()
            .map { it.toDomain() }

    /**
     * Фото-доказ рецензента — у приватний bucket review-photos, у теку заявки (так вимагає storage-політика
     * і approve_place_request). Повертає шлях у bucket'і.
     */
    suspend fun uploadReviewPhoto(requestId: String, bytes: ByteArray, fileExtension: String): String {
        val path = "$requestId/${UUID.randomUUID()}.$fileExtension"
        try {
            client.storage.from(REVIEW_PHOTOS_BUCKET).upload(path, bytes)
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /storage/v1/object/$REVIEW_PHOTOS_BUCKET", e, mapOf("path" to path))
            throw e
        }
        return path
    }

    /** Схвалити можна лише з фото-доказом: сервер перевіряє, що файл [photoPath] справді завантажено. */
    suspend fun approve(requestId: String, photoPath: String) {
        val params = mapOf("p_request_id" to requestId, "p_photo_path" to photoPath)
        try {
            client.postgrest.rpc(
                "approve_place_request",
                buildJsonObject {
                    put("p_request_id", requestId)
                    put("p_photo_path", photoPath)
                }
            )
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/rpc/approve_place_request", e, params)
            throw e
        }
    }

    suspend fun reject(requestId: String, reason: String?) {
        try {
            client.postgrest.rpc(
                "reject_place_request",
                buildJsonObject {
                    put("p_request_id", requestId)
                    reason?.takeIf { it.isNotBlank() }?.let { put("p_reason", it) }
                }
            )
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/rpc/reject_place_request", e, mapOf("p_request_id" to requestId))
            throw e
        }
    }

    private fun categoryApiValue(category: PlaceCategory): String = category.name.lowercase()

    /**
     * submit_place_request повертає один рядок (returns place_requests, не setof), і PostgREST віддає
     * JSON-об'єкт, а не масив — decodeSingle() (= decodeList().first()) на ньому падав, хоча заявку вже
     * створено. Приймаємо обидві форми.
     */
    private fun singleRow(json: JsonElement): PlaceRequestDto {
        val row = if (json is JsonArray) json.first() else json
        return rowJson.decodeFromJsonElement(PlaceRequestDto.serializer(), row)
    }

    private fun PlaceRequestDto.toDomain() = PlaceRequest(
        id = id,
        requesterId = requesterId,
        name = name,
        address = address,
        category = category,
        note = note,
        status = status,
        rejectReason = rejectReason,
        createdAt = createdAt
    )
}
