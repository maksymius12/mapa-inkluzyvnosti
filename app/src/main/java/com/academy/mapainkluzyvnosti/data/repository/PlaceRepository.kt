package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.Place
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

class PlaceRepository(private val client: SupabaseClient) {

    private val photoBucket get() = client.storage.from("place-photos")

    suspend fun getAllPlaces(): List<Place> {
        val places = client.postgrest.from("places").select().decodeList<PlaceReadDto>()
        return places.map { it.toDomain(latestCheck = null) }
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

    suspend fun updatePlaceStatus(placeId: String, status: AccessStatus) {
        client.postgrest.from("places").update({
            set("status", status)
        }) { filter { eq("id", placeId) } }
    }

    suspend fun uploadPlacePhoto(placeId: String, userId: String, bytes: ByteArray, fileExtension: String): PlacePhotoDto {
        val path = "$placeId/${UUID.randomUUID()}.$fileExtension"
        photoBucket.upload(path, bytes)
        val publicUrl = photoBucket.publicUrl(path)

        val analysis = analyzePhoto(publicUrl)

        val photo = PlacePhotoDto(
            placeId = placeId,
            userId = userId,
            url = publicUrl,
            aiTitle = analysis?.title,
            aiSuggestions = analysis?.suggestions.orEmpty(),
            aiSuggestedStatus = analysis?.status
        )
        return client.postgrest.from("place_photos").insert(photo) { select() }.decodeSingle()
    }

    private suspend fun analyzePhoto(photoUrl: String): AnalyzePlacePhotoResponse? {
        return try {
            client.functions.invoke(
                function = "analyze-place-photo",
                body = AnalyzePlacePhotoRequest(photoUrl)
            ).body<AnalyzePlacePhotoResponse>()
        } catch (e: Exception) {
            null
        }
    }

    private fun PlaceReadDto.toDomain(latestCheck: CheckDto?) = Place(
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
        reviewCount = reviewCount
    )
}
