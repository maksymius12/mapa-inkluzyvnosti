package com.academy.mapainkluzyvnosti.data.remote.dto

import com.academy.mapainkluzyvnosti.data.model.ChatMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Рядок RPC get_chat_messages(): без user_id і email, лише ім'я автора. */
@Serializable
data class ChatMessageDto(
    val id: String,
    @SerialName("author_name") val authorName: String? = null,
    val text: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("is_mine") val isMine: Boolean = false
) {
    fun toDomain() = ChatMessage(
        id = id,
        authorName = authorName?.takeIf { it.isNotBlank() } ?: "Користувач",
        text = text,
        createdAt = createdAt,
        isMine = isMine
    )
}

/** Вставка: лише текст. user_id і час ставить база (права на інші колонки клієнту не видано). */
@Serializable
data class ChatMessageInsertDto(val text: String)
