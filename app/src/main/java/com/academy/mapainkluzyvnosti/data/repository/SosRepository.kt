package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.local.LocationFix
import com.academy.mapainkluzyvnosti.data.local.LocationSource
import com.academy.mapainkluzyvnosti.data.model.SosProblemType
import com.academy.mapainkluzyvnosti.data.model.SosRequest
import com.academy.mapainkluzyvnosti.data.model.SosStatus
import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import com.academy.mapainkluzyvnosti.data.remote.dto.SosRequestDto
import com.academy.mapainkluzyvnosti.data.remote.dto.SosRequestInsertDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.PostgrestUpdate
import java.time.Instant

/**
 * SOS-запити. Права перевіряє сервер (міграція 20261102120000): читають автор і рецензенти; автор уточнює
 * тип, коментар, локацію й може скасувати; рецензент — лише «вирішено».
 */
class SosRepository(private val client: SupabaseClient) {

    /** Створює запит одразу — тип проблеми й коментар можна додати потім ([updateDetails]). */
    suspend fun createRequest(userId: String, location: LocationFix?, problemType: SosProblemType?, comment: String?): String {
        val dto = SosRequestInsertDto(
            userId = userId,
            lat = location?.point?.lat,
            lng = location?.point?.lng,
            locationSource = (location?.source ?: LocationSource.NONE).apiValue,
            locationAccuracyM = location?.accuracyM,
            locationAt = location?.let { Instant.ofEpochMilli(it.timeMs).toString() },
            problemType = problemType,
            comment = comment
        )
        return try {
            client.postgrest.from("sos_requests").insert(dto) { select() }.decodeSingle<SosRequestDto>().id
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/sos_requests", e, mapOf("location_source" to dto.locationSource))
            throw e
        }
    }

    suspend fun updateDetails(id: String, problemType: SosProblemType?, comment: String?) = update(id, "details") {
        set("problem_type", problemType)
        set("comment", comment)
    }

    suspend fun updateLocation(id: String, location: LocationFix) = update(id, "location") {
        set("lat", location.point.lat)
        set("lng", location.point.lng)
        set("location_source", location.source.apiValue)
        set("location_accuracy_m", location.accuracyM)
        set("location_at", Instant.ofEpochMilli(location.timeMs).toString())
    }

    suspend fun cancel(id: String) = update(id, "cancel") { set("status", SosStatus.CANCELLED) }

    suspend fun resolve(id: String) = update(id, "resolve") { set("status", SosStatus.RESOLVED) }

    suspend fun getOpenRequests(): List<SosRequest> =
        client.postgrest.from("sos_requests")
            // Рядок, а не enum: фільтр бере toString() — «OPEN» не збігався з «open», і відкриті SOS не приходили.
            .select { filter { eq("status", "open") } }
            .decodeList<SosRequestDto>()
            .map { it.toDomain() }

    private suspend fun update(id: String, what: String, block: PostgrestUpdate.() -> Unit) {
        try {
            client.postgrest.from("sos_requests").update(block) { filter { eq("id", id) } }
        } catch (e: Exception) {
            SupabaseErrorLog.log("PATCH /rest/v1/sos_requests ($what)", e, mapOf("id" to id))
            throw e
        }
    }
}
