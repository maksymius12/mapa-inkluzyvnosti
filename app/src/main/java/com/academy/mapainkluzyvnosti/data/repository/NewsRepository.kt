package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class NewsPost(
    val id: String,
    val title: String,
    val body: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class NewsInsertDto(
    @SerialName("author_id") val authorId: String,
    val title: String,
    val body: String,
    @SerialName("photo_url") val photoUrl: String?
)

/** Стрічка «Новини» (news_posts, міграція 20261103130000): читають усі, публікує лише адмін (RLS). */
class NewsRepository(private val client: SupabaseClient) {

    suspend fun list(): List<NewsPost> =
        client.postgrest.from("news_posts")
            .select {
                order("created_at", Order.DESCENDING)
                limit(100)
            }
            .decodeList<NewsPost>()

    /** Фото — у place-photos у теці автора (так його дозволено завантажити й видалити). */
    suspend fun publish(authorId: String, title: String, body: String, photoJpeg: ByteArray?) {
        val photoUrl = photoJpeg?.let { bytes ->
            val path = "$authorId/news/${UUID.randomUUID()}.jpg"
            try {
                client.storage.from("place-photos").upload(path, bytes)
            } catch (e: Exception) {
                SupabaseErrorLog.log("POST /storage/v1/object/place-photos (news)", e, mapOf("path" to path))
                throw e
            }
            client.storage.from("place-photos").publicUrl(path)
        }
        try {
            client.postgrest.from("news_posts").insert(NewsInsertDto(authorId, title.trim(), body.trim(), photoUrl))
        } catch (e: Exception) {
            SupabaseErrorLog.log("POST /rest/v1/news_posts", e, mapOf("title_length" to title.length))
            throw e
        }
    }

    /** Видаляє допис і його фото (файл — якщо він у теці цього ж адміна; інакше лише логуємо). */
    suspend fun delete(post: NewsPost) {
        try {
            client.postgrest.from("news_posts").delete { filter { eq("id", post.id) } }
        } catch (e: Exception) {
            SupabaseErrorLog.log("DELETE /rest/v1/news_posts", e, mapOf("id" to post.id))
            throw e
        }
        val path = post.photoUrl?.substringAfter("/object/public/place-photos/", "")?.takeIf { it.isNotBlank() } ?: return
        runCatching { client.storage.from("place-photos").delete(path) }
            .onFailure { e -> SupabaseErrorLog.log("DELETE /storage/v1/object/place-photos (news)", e, mapOf("path" to path)) }
    }
}
