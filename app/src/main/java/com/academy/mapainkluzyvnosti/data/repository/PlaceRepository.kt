package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.OpeningHours
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import com.academy.mapainkluzyvnosti.data.remote.dto.AnalyzePlacePhotoRequest
import com.academy.mapainkluzyvnosti.data.remote.dto.AnalyzePlacePhotoResponse
import com.academy.mapainkluzyvnosti.data.remote.dto.CheckDto
import com.academy.mapainkluzyvnosti.data.remote.dto.toDomainCheckResult
import com.academy.mapainkluzyvnosti.data.remote.dto.PlacePhotoDto
import com.academy.mapainkluzyvnosti.data.remote.dto.PlaceReadDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.client.call.body
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Завантажений у бакет файл фото (ще без рядка в place_photos). */
data class UploadedPhoto(val path: String, val url: String)

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

    /** Графік роботи — лише волонтер / учень / адмін (RPC set_place_hours перевіряє роль). null — прибрати. */
    suspend fun setOpeningHours(placeId: String, hours: OpeningHours?) {
        try {
            client.postgrest.rpc(
                "set_place_hours",
                buildJsonObject {
                    put("p_place_id", placeId)
                    put("p_hours", hours?.toJson() ?: kotlinx.serialization.json.JsonNull)
                }
            )
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/rpc/set_place_hours", e, mapOf("p_place_id" to placeId))
            throw e
        }
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

    /** Файл у бакеті place-photos: тека — id автора, щоб відхилене чи скасоване фото можна було видалити. */
    suspend fun uploadPhotoFile(placeId: String, userId: String, bytes: ByteArray, fileExtension: String): UploadedPhoto {
        val path = "$userId/$placeId/${UUID.randomUUID()}.$fileExtension"
        try {
            photoBucket.upload(path, bytes)
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /storage/v1/object/place-photos", e, mapOf("path" to path))
            throw e
        }
        return UploadedPhoto(path, photoBucket.publicUrl(path))
    }

    /** Мем / скасовано: файл прибираємо, рядок у place_photos не створюємо. Помилку лише логуємо. */
    suspend fun deletePhotoFile(path: String) {
        runCatching { photoBucket.delete(path) }
            .onFailure { e -> SupabaseErrorLog.log("DELETE /storage/v1/object/place-photos", e, mapOf("path" to path)) }
    }

    /**
     * Рядок фото. [applyStatus] = false («Лише завантажити фото») — статус ШІ не зберігаємо: інакше тригер
     * checks_after_insert_apply врахував би його при наступній перевірці, а дані місця не мали змінюватися.
     */
    suspend fun savePhoto(placeId: String, userId: String, url: String, analysis: AnalyzePlacePhotoResponse?, applyStatus: Boolean): PlacePhotoDto {
        val photo = PlacePhotoDto(
            placeId = placeId,
            userId = userId,
            url = url,
            aiTitle = analysis?.title,
            aiSuggestions = analysis?.suggestions,
            aiSuggestedStatus = if (applyStatus) analysis?.status else null
        )
        return try {
            client.postgrest.from("place_photos").insert(photo) { select() }.decodeSingle<PlacePhotoDto>()
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/place_photos", e, mapOf("place_id" to placeId))
            throw e
        }
    }

    /**
     * AI-аналіз фото через Edge Function analyze-place-photo (OpenRouter на сервері; ключа в застосунку немає).
     * Помилку кидає далі — екран пропонує завантажити фото без аналізу.
     */
    suspend fun analyzePhoto(photoUrl: String): AnalyzePlacePhotoResponse {
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
        attribution = attribution,
        openingHours = OpeningHours.parse(openingHours)
    )
}
