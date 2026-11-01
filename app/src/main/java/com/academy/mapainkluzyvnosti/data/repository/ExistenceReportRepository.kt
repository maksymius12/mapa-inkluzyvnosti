package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.PlaceRequestStatus
import com.academy.mapainkluzyvnosti.data.remote.NothingUpdatedException
import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import com.academy.mapainkluzyvnosti.data.remote.dto.CreatedPlaceIdDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Звіт «Цього місця не існує» у списку адміна (з назвою місця й автором). */
data class ExistenceReport(
    val id: String,
    val placeId: String,
    val placeName: String,
    val placeAddress: String?,
    val placeHidden: Boolean,
    val reporterName: String?,
    val reporterEmail: String?,
    val comment: String,
    val photoUrl: String?,
    val status: PlaceRequestStatus,
    val createdAt: String
)

@Serializable
private data class ExistenceReportInsertDto(
    @SerialName("place_id") val placeId: String,
    @SerialName("reported_by") val reportedBy: String,
    val comment: String,
    @SerialName("photo_url") val photoUrl: String?
)

@Serializable
private data class ExistenceReportRowDto(
    val id: String,
    @SerialName("place_id") val placeId: String,
    @SerialName("place_name") val placeName: String,
    @SerialName("place_address") val placeAddress: String? = null,
    @SerialName("place_hidden") val placeHidden: Boolean = false,
    @SerialName("reporter_name") val reporterName: String? = null,
    @SerialName("reporter_email") val reporterEmail: String? = null,
    val comment: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    val status: PlaceRequestStatus,
    @SerialName("created_at") val createdAt: String
)

/**
 * Звіти «Цього місця не існує» (place_existence_reports, міграція 20261101120000).
 * Подати — волонтер/учень/адмін; переглядати всі, схвалювати й відхиляти — лише адмін (перевіряє сервер).
 */
class ExistenceReportRepository(private val client: SupabaseClient) {

    private val photoBucket get() = client.storage.from("place-photos")

    /** Фото (необов'язкове) — у теку автора, як і фото місць. */
    suspend fun submit(placeId: String, userId: String, comment: String, photoJpeg: ByteArray?) {
        val path = photoJpeg?.let { "$userId/existence/$placeId/${UUID.randomUUID()}.jpg" }
        val photoUrl = if (path != null && photoJpeg != null) {
            try {
                photoBucket.upload(path, photoJpeg)
            } catch (e: Exception) {
                SupabaseErrorLog.log("POST /storage/v1/object/place-photos (existence)", e, mapOf("place_id" to placeId))
                throw e
            }
            photoBucket.publicUrl(path)
        } else {
            null
        }
        try {
            client.postgrest.from("place_existence_reports")
                .insert(ExistenceReportInsertDto(placeId, userId, comment, photoUrl))
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/place_existence_reports", e, mapOf("place_id" to placeId))
            if (path != null) runCatching { photoBucket.delete(path) }
            throw e
        }
    }

    /** [status] = null — усі звіти. */
    suspend fun adminList(status: PlaceRequestStatus?): List<ExistenceReport> =
        try {
            client.postgrest.rpc(
                "admin_list_existence_reports",
                buildJsonObject { put("p_status", status?.apiValue) }
            ).decodeList<ExistenceReportRowDto>().map { it.toDomain() }
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/rpc/admin_list_existence_reports", e)
            throw e
        }

    suspend fun approve(reportId: String) = rpc("approve_existence_report", reportId)

    suspend fun reject(reportId: String) = rpc("reject_existence_report", reportId)

    suspend fun delete(reportId: String) {
        val operation = "DELETE /rest/v1/place_existence_reports"
        val deleted = try {
            client.postgrest.from("place_existence_reports").delete {
                select(Columns.list("id"))
                filter { eq("id", reportId) }
            }.decodeList<CreatedPlaceIdDto>()
        } catch (e: Exception) {
            SupabaseErrorLog.log(operation, e, mapOf("id" to reportId))
            throw e
        }
        if (deleted.isEmpty()) throw NothingUpdatedException(operation)
    }

    private suspend fun rpc(function: String, reportId: String) {
        try {
            client.postgrest.rpc(function, buildJsonObject { put("p_report_id", reportId) })
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/rpc/$function", e, mapOf("p_report_id" to reportId))
            throw e
        }
    }

    private fun ExistenceReportRowDto.toDomain() = ExistenceReport(
        id = id,
        placeId = placeId,
        placeName = placeName,
        placeAddress = placeAddress,
        placeHidden = placeHidden,
        reporterName = reporterName,
        reporterEmail = reporterEmail,
        comment = comment,
        photoUrl = photoUrl,
        status = status,
        createdAt = createdAt
    )
}

private val PlaceRequestStatus.apiValue: String
    get() = when (this) {
        PlaceRequestStatus.PENDING -> "pending"
        PlaceRequestStatus.APPROVED -> "approved"
        PlaceRequestStatus.REJECTED -> "rejected"
    }
