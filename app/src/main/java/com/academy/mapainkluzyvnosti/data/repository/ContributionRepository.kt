package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Одна власна перевірка: місце (якщо воно ще видиме), коли перевірено, поточний статус місця. */
data class OwnCheck(val placeId: String, val placeName: String?, val placeStatus: AccessStatus?, val checkedAt: String?)

/** Що зробив користувач — лише з реальних таблиць; null — не вдалося прочитати. */
data class Contribution(
    val checks: List<OwnCheck>,
    val addedPlaces: Int?,
    val reviews: Int?,
    val approvedRequests: Int?
)

/**
 * «Твій внесок» у профілі. Лише власні дані: перевірки (RPC my_checks), додані місця (places.created_by),
 * відгуки (place_reviews) і схвалені заявки (place_requests — RLS і так віддає мешканцю лише свої).
 * Окремої історії балів у базі немає: бали нараховує тригер checks_after_insert_apply (+10 за перевірку).
 */
class ContributionRepository(private val client: SupabaseClient) {

    suspend fun load(userId: String): Contribution {
        val checks = runCatching { ownChecks(userId) }
            .onFailure { SupabaseErrorLog.log("GET /rest/v1/checks (own)", it, mapOf("user_id" to userId)) }
            .getOrThrow()
        return Contribution(
            checks = checks,
            addedPlaces = countOrNull("places", "created_by", userId),
            reviews = countOrNull("place_reviews", "user_id", userId),
            approvedRequests = countOrNull("place_requests", "requester_id", userId, status = "approved")
        )
    }

    private suspend fun ownChecks(userId: String): List<OwnCheck> {
        // user_id у checks клієнтам не видно (міграція 20261105120000) — власні перевірки дає RPC my_checks()
        // за auth.uid(), тож чужих тут бути не може навіть за помилки в запиті.
        val rows = client.postgrest.rpc("my_checks", buildJsonObject { put("p_limit", MAX_CHECKS) })
            .decodeList<CheckRow>()
        if (rows.isEmpty()) return emptyList()
        // Приховані місця RLS не віддає — такі рядки лишаються без назви й без переходу.
        val places = client.postgrest.from("places")
            .select(Columns.list("id", "name", "status")) { filter { isIn("id", rows.map { it.placeId }.distinct()) } }
            .decodeList<PlaceRow>()
            .associateBy { it.id }
        return rows.map { row ->
            val place = places[row.placeId]
            OwnCheck(
                placeId = row.placeId,
                placeName = place?.name,
                placeStatus = place?.status?.let { s -> AccessStatus.entries.firstOrNull { it.name.equals(s, ignoreCase = true) } },
                checkedAt = row.createdAt
            )
        }
    }

    private suspend fun countOrNull(table: String, column: String, userId: String, status: String? = null): Int? =
        runCatching {
            client.postgrest.from(table)
                .select(Columns.list("id")) {
                    filter {
                        eq(column, userId)
                        if (status != null) eq("status", status)
                    }
                }
                .decodeList<IdRow>()
                .size
        }.onFailure { SupabaseErrorLog.log("GET /rest/v1/$table (own count)", it, mapOf("user_id" to userId)) }
            .getOrNull()

    @Serializable
    private data class CheckRow(@SerialName("place_id") val placeId: String, @SerialName("created_at") val createdAt: String? = null)

    @Serializable
    private data class PlaceRow(val id: String, val name: String? = null, val status: String? = null)

    @Serializable
    private data class IdRow(val id: String)

    private companion object {
        const val MAX_CHECKS = 200
    }
}
