package com.academy.mapainkluzyvnosti.data.model

/**
 * Повідомлення спільного чату. Автора видно лише за іменем із профілю: user_id сервер не віддає
 * (міграція 20261111130000_chat_messages.sql), [isMine] — чи це повідомлення поточного користувача.
 */
data class ChatMessage(
    val id: String,
    val authorName: String,
    val text: String,
    /** ISO-час із сервера. */
    val createdAt: String,
    val isMine: Boolean
)
