package com.academy.mapainkluzyvnosti.data.repository

import com.academy.mapainkluzyvnosti.data.model.ChatMessage
import com.academy.mapainkluzyvnosti.data.remote.NothingUpdatedException
import com.academy.mapainkluzyvnosti.data.remote.SupabaseErrorLog
import com.academy.mapainkluzyvnosti.data.remote.dto.ChatMessageDto
import com.academy.mapainkluzyvnosti.data.remote.dto.ChatMessageInsertDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Спільний чат (міграція 20261111130000_chat_messages.sql). Права й ліміти перевіряє сервер:
 * читати й писати — лише після входу, текст 1–500 символів, не частіше 1 повідомлення на 2 с і 30 за хвилину,
 * «видалити» (deleted = true) — автор своє, адмін будь-яке.
 */
class ChatRepository(private val client: SupabaseClient) {

    /** Останні [limit] повідомлень, старі першими. */
    suspend fun latest(limit: Int = PAGE_SIZE): List<ChatMessage> = logged("POST /rest/v1/rpc/get_chat_messages") {
        client.postgrest.rpc("get_chat_messages", buildJsonObject { put("p_limit", limit) })
            .decodeList<ChatMessageDto>()
            .map { it.toDomain() }
    }

    /** Без select(): клієнт не має права читати user_id, тож повертати вставлений рядок не можна. */
    suspend fun send(text: String) {
        logged("POST /rest/v1/chat_messages", mapOf("length" to text.length)) {
            client.postgrest.from("chat_messages").insert(ChatMessageInsertDto(text))
        }
    }

    suspend fun markDeleted(id: String) {
        val rows = logged("PATCH /rest/v1/chat_messages (delete)", mapOf("id" to id)) {
            client.postgrest.from("chat_messages")
                .update({ set("deleted", true) }) {
                    select(Columns.list("id"))
                    filter { eq("id", id) }
                }
                .decodeList<IdRow>()
        }
        // 0 рядків — повідомлення вже видалене або немає прав (RLS).
        if (rows.isEmpty()) throw NothingUpdatedException("chat_messages delete")
    }

    private suspend fun <T> logged(operation: String, fields: Map<String, Any?> = emptyMap(), block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SupabaseErrorLog.log(operation, e, fields)
            throw e
        }

    @Serializable
    private data class IdRow(val id: String)

    companion object {
        const val PAGE_SIZE = 100
    }
}
