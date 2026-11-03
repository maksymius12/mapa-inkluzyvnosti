package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.PlaceRequestStatus
import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Пропозиція мешканця змінити назву місця (place_name_suggestions). */
@Serializable
data class NameSuggestion(
    val id: String,
    @SerialName("place_id") val placeId: String,
    @SerialName("suggested_by") val suggestedBy: String,
    @SerialName("current_name") val currentName: String,
    @SerialName("new_name") val newName: String,
    val comment: String? = null,
    val status: PlaceRequestStatus,
    @SerialName("created_at") val createdAt: String
)

/**
 * Пропозиції зміни назви (міграція 20261102120000). Подати може будь-хто з акаунтом; розглядають волонтер,
 * учень і адмін. Писати в таблицю напряму не можна — лише RPC, які самі перевіряють права.
 */
class NameSuggestionRepository(private val client: SupabaseClient) {

    suspend fun suggest(placeId: String, newName: String, comment: String?) {
        try {
            client.postgrest.rpc(
                "suggest_place_name",
                buildJsonObject {
                    put("p_place_id", placeId)
                    put("p_new_name", newName)
                    comment?.takeIf { it.isNotBlank() }?.let { put("p_comment", it) }
                }
            )
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/rpc/suggest_place_name", e, mapOf("p_place_id" to placeId))
            throw e
        }
    }

    /** RLS: рецензент бачить усі пропозиції, автор — лише свої. */
    suspend fun pending(): List<NameSuggestion> =
        client.postgrest.from("place_name_suggestions")
            .select {
                filter { eq("status", "pending") }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<NameSuggestion>()

    suspend fun mine(userId: String): List<NameSuggestion> =
        client.postgrest.from("place_name_suggestions")
            .select {
                filter { eq("suggested_by", userId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<NameSuggestion>()

    suspend fun approve(id: String) = call("approve_name_suggestion", id)

    suspend fun reject(id: String) = call("reject_name_suggestion", id)

    private suspend fun call(function: String, id: String) {
        try {
            client.postgrest.rpc(function, buildJsonObject { put("p_suggestion_id", id) })
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/rpc/$function", e, mapOf("p_suggestion_id" to id))
            throw e
        }
    }
}
