package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import com.academy.mapainkluzyvnosti.data.remote.dto.AnalyzePlacePhotoRequest
import com.academy.mapainkluzyvnosti.data.remote.dto.AnalyzePlacePhotoResponse
import com.academy.mapainkluzyvnosti.data.remote.dto.CheckDto
import com.academy.mapainkluzyvnosti.data.remote.dto.toDomainCheckResult
import com.academy.mapainkluzyvnosti.data.remote.dto.PlacePhotoDto
import com.academy.mapainkluzyvnosti.data.remote.dto.PlaceReadDto
import com.academy.mapainkluzyvnosti.data.remote.dto.PlaceWriteDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.client.call.body
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Результат додавання фото місця. */
sealed interface PhotoUploadResult {
    /** Фото збережено разом із результатом ШІ-аналізу. */
    data class Analyzed(val photo: PlacePhotoDto) : PhotoUploadResult

    /** Фото збережено, але ШІ-аналіз не вдався (мережа чи помилка функції). */
    data class AnalysisFailed(val photo: PlacePhotoDto) : PhotoUploadResult

    /** ШІ вважає, що на фото не реальне місце — фото не збережено. */
    data class Rejected(val reason: String) : PhotoUploadResult
}

class PlaceRepository(private val client: SupabaseClient) {

    private val photoBucket get() = client.storage.from("place-photos")

    suspend fun getAllPlaces(): List<Place> {
        // Адмін читає й приховані місця (RLS "admin reads all places"), але на мапі й у списках їх не показуємо.
        val places = client.postgrest.from("places")
            .select { filter { eq("is_hidden", false) } }
            .decodeList<PlaceReadDto>()
        // Тип доступності на мапі береться з останньої перевірки кожного закладу.
        val latestChecks = runCatching { latestChecksByPlace() }.getOrDefault(emptyMap())
        return places.map { it.toDomain(latestCheck = latestChecks[it.id]) }
    }

    private suspend fun latestChecksByPlace(): Map<String, CheckDto> =
        client.postgrest.from("checks")
            .select { order("created_at", Order.DESCENDING) }
            .decodeList<CheckDto>()
            .groupBy { it.placeId }
            .mapValues { (_, checks) -> checks.first() }

    /** Останнє фото кожного з [placeIds] (для мініатюр у списках) одним запитом. */
    suspend fun getLatestPhotoUrls(placeIds: Collection<String>): Map<String, String> {
        if (placeIds.isEmpty()) return emptyMap()
        return client.postgrest.from("place_photos")
            .select {
                filter { isIn("place_id", placeIds.toList()) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<PlacePhotoDto>()
            .groupBy { it.placeId }
            .mapValues { (_, photos) -> photos.first().url }
    }

    suspend fun getPlaceById(id: String): Place? {
        val dto = client.postgrest.from("places")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<PlaceReadDto>() ?: return null
        val latestCheck = latestCheckFor(id)
        return dto.toDomain(latestCheck)
    }

    suspend fun placesNearby(lat: Double, lng: Double, radiusMeters: Double): List<Place> {
        val result = client.postgrest.rpc(
            "places_nearby",
            buildJsonObject {
                put("lat", lat)
                put("lng", lng)
                put("radius_m", radiusMeters)
            }
        )
        return result.decodeList<PlaceReadDto>().map { it.toDomain(latestCheck = null) }
    }

    suspend fun getLatestPhoto(placeId: String): PlacePhotoDto? =
        client.postgrest.from("place_photos")
            .select {
                filter { eq("place_id", placeId) }
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeSingleOrNull<PlacePhotoDto>()

    private suspend fun latestCheckFor(placeId: String): CheckDto? =
        client.postgrest.from("checks")
            .select {
                filter { eq("place_id", placeId) }
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeSingleOrNull<CheckDto>()

    suspend fun importOsmPlaces(places: List<PlaceWriteDto>) {
        if (places.isEmpty()) return
        client.postgrest.from("places").upsert(places)
    }

    /**
     * Запасний шлях для бази без тригера checks_after_insert_apply: статус і паркування з клієнта.
     * Помилки лише логуються — сама перевірка вже збережена, а з тригером сервер робить це сам.
     */
    suspend fun applyCheckToPlaceFallback(placeId: String, status: AccessStatus, hasAccessibleParking: Boolean) {
        try {
            client.postgrest.from("places").update({
                set("status", status)
                set("has_accessible_parking", hasAccessibleParking)
            }) { filter { eq("id", placeId) } }
        } catch (e: Exception) {
            SupabaseErrorLog.log(
                "PATCH /rest/v1/places (status after check)",
                e,
                mapOf("id" to placeId, "status" to status, "has_accessible_parking" to hasAccessibleParking)
            )
        }
    }

    /**
     * Завантажує фото й віддає його на ШІ-аналіз. Тека — id автора: так файл відхиленого фото
     * можна видалити (storage-політика photos_storage_delete_own).
     *  - ШІ вважає, що на фото не місце → файл видаляємо, рядок не створюємо: [PhotoUploadResult.Rejected];
     *  - аналіз не вдався (мережа, 4xx/5xx) → фото все одно зберігаємо: [PhotoUploadResult.AnalysisFailed].
     */
    suspend fun uploadPlacePhoto(placeId: String, userId: String, bytes: ByteArray, fileExtension: String): PhotoUploadResult {
        val path = "$userId/$placeId/${UUID.randomUUID()}.$fileExtension"
        try {
            photoBucket.upload(path, bytes)
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /storage/v1/object/place-photos", e, mapOf("path" to path))
            throw e
        }
        val publicUrl = photoBucket.publicUrl(path)

        val analysis = runCatching { analyzePhoto(publicUrl) }
        analysis.getOrNull()?.takeIf { !it.isPlacePhoto }?.let { rejected ->
            runCatching { photoBucket.delete(path) }
                .onFailure { e -> SupabaseErrorLog.log("DELETE /storage/v1/object/place-photos", e, mapOf("path" to path)) }
            return PhotoUploadResult.Rejected(rejected.title)
        }

        val result = analysis.getOrNull()
        val photo = PlacePhotoDto(
            placeId = placeId,
            userId = userId,
            url = publicUrl,
            aiTitle = result?.title,
            aiSuggestions = result?.suggestions.orEmpty(),
            aiSuggestedStatus = result?.status
        )
        val saved = try {
            client.postgrest.from("place_photos").insert(photo) { select() }.decodeSingle<PlacePhotoDto>()
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/place_photos", e, mapOf("place_id" to placeId))
            throw e
        }
        return if (result != null) PhotoUploadResult.Analyzed(saved) else PhotoUploadResult.AnalysisFailed(saved)
    }

    /**
     * AI-аналіз фото через Edge Function analyze-place-photo (OpenRouter на сервері; ключа в застосунку немає).
     * Помилку кидає далі — uploadPlacePhoto зберігає фото без підказок і каже про це користувачу.
     */
    private suspend fun analyzePhoto(photoUrl: String): AnalyzePlacePhotoResponse {
        return try {
            client.functions.invoke(
                function = "analyze-place-photo",
                body = AnalyzePlacePhotoRequest(photoUrl)
            ).body<AnalyzePlacePhotoResponse>()
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /functions/v1/analyze-place-photo", e)
            throw e
        }
    }

    fun PlaceReadDto.toDomain(latestCheck: CheckDto?) = Place(
        id = id,
        name = name,
        address = address,
        category = category,
        lat = lat,
        lng = lng,
        status = status,
        checks = latestCheck?.toDomainCheckResult(),
        source = source,
        verifiedNote = verifiedNote,
        rating = rating,
        reviewCount = reviewCount,
        hasAccessibleParking = hasAccessibleParking,
        createdBy = createdBy,
        attribution = attribution
    )
}
