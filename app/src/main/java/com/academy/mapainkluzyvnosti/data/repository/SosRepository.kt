package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.SosProblemType
import com.academy.mapainkluzyvnosti.data.model.SosRequest
import com.academy.mapainkluzyvnosti.data.model.SosStatus
import com.academy.mapainkluzyvnosti.data.remote.dto.SosRequestDto
import com.academy.mapainkluzyvnosti.data.remote.dto.SosRequestInsertDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant

class SosRepository(private val client: SupabaseClient) {

    /** Створює SOS-запит і повертає його id (щоб потім уточнити координати). */
    suspend fun createRequest(userId: String, lat: Double, lng: Double, problemType: SosProblemType, comment: String?): String =
        client.postgrest.from("sos_requests").insert(
            SosRequestInsertDto(userId = userId, lat = lat, lng = lng, problemType = problemType, comment = comment)
        ) { select() }.decodeSingle<SosRequestDto>().id

    /** Точніші координати вже надісланого запиту — коли свіжий GPS-фікс з'явився після відправки. */
    suspend fun updateLocation(id: String, lat: Double, lng: Double) {
        client.postgrest.from("sos_requests").update({
            set("lat", lat)
            set("lng", lng)
        }) { filter { eq("id", id) } }
    }

    suspend fun getOpenRequests(): List<SosRequest> =
        client.postgrest.from("sos_requests")
            .select { filter { eq("status", SosStatus.OPEN) } }
            .decodeList<SosRequestDto>()
            .map { it.toDomain() }

    suspend fun resolve(id: String, resolverId: String) {
        client.postgrest.from("sos_requests").update({
            set("status", SosStatus.RESOLVED)
            set("resolved_by", resolverId)
            set("resolved_at", Instant.now().toString())
        }) { filter { eq("id", id) } }
    }
}
