package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.PlaceReview
import com.academy.mapainkluzyvnosti.data.remote.NothingUpdatedException
import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import com.academy.mapainkluzyvnosti.data.remote.dto.CreatedPlaceIdDto
import com.academy.mapainkluzyvnosti.data.remote.dto.PlaceReviewDto
import com.academy.mapainkluzyvnosti.data.remote.dto.PlaceReviewInsertDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Відгуки до місць (place_reviews). Права перевіряє сервер: читати — усі, писати — лише від свого імені,
 * змінювати — автор або адмін. Середній рейтинг місця оновлює тригер.
 */
class ReviewRepository(private val client: SupabaseClient) {

    suspend fun reviews(placeId: String): List<PlaceReview> =
        client.postgrest.from("place_reviews")
            .select {
                filter { eq("place_id", placeId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<PlaceReviewDto>()
            .map { it.toDomain() }

    suspend fun addReview(placeId: String, userId: String, rating: Int, comment: String?) {
        try {
            client.postgrest.from("place_reviews").insert(PlaceReviewInsertDto(placeId, userId, rating, comment))
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/place_reviews", e, mapOf("place_id" to placeId, "rating" to rating))
            throw e
        }
    }

    suspend fun updateReview(reviewId: String, rating: Int, comment: String?) {
        val operation = "PATCH /rest/v1/place_reviews"
        val updated = try {
            client.postgrest.from("place_reviews").update(
                buildJsonObject {
                    put("rating", rating)
                    put("comment", comment)
                }
            ) {
                select(Columns.list("id"))
                filter { eq("id", reviewId) }
            }.decodeList<CreatedPlaceIdDto>()
        } catch (e: Exception) {
            SupabaseErrorLog.log(operation, e, mapOf("id" to reviewId, "rating" to rating))
            throw e
        }
        if (updated.isEmpty()) throw NothingUpdatedException(operation)
    }

    private fun PlaceReviewDto.toDomain() = PlaceReview(
        id = id,
        userId = userId,
        authorName = authorName?.takeIf { it.isNotBlank() } ?: "Користувач",
        rating = rating,
        comment = comment,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
