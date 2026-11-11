package com.academy.mapainkluzyvnosti.ui.screens.chat

import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Межа довжини — та сама, що CHECK у міграції 20261111130000_chat_messages.sql. */
const val CHAT_MAX_LENGTH = 500

/** Лічильник символів з'являється, коли до межі лишається мало. */
const val CHAT_COUNTER_FROM = 400

/** Як часто оновлювати чат, поки екран відкритий. */
const val CHAT_POLL_INTERVAL_MS = 5_000L

/**
 * Довжина так, як її рахує Postgres (char_length — кодові точки): емодзі — один символ,
 * хоча в Kotlin String.length це 2.
 */
fun chatTextLength(text: String): Int = text.codePointCount(0, text.length)

enum class ChatTextProblem { EMPTY, TOO_LONG }

/** Що саме піде на сервер: без пробілів і переносів на краях (база перевіряє те саме). */
fun normalizeChatText(raw: String): String = raw.trim()

/** null — можна надсилати. */
fun validateChatText(raw: String): ChatTextProblem? {
    val text = normalizeChatText(raw)
    return when {
        text.isEmpty() -> ChatTextProblem.EMPTY
        chatTextLength(text) > CHAT_MAX_LENGTH -> ChatTextProblem.TOO_LONG
        else -> null
    }
}

/** «430/500», коли текст наближається до межі; інакше null. */
fun chatCounterText(raw: String): String? {
    val length = chatTextLength(normalizeChatText(raw))
    return if (length >= CHAT_COUNTER_FROM) "$length/$CHAT_MAX_LENGTH" else null
}

/** Видалити може автор своє, адмін — будь-яке (так само перевіряє RLS). */
fun canDeleteChatMessage(isMine: Boolean, isAdmin: Boolean): Boolean = isMine || isAdmin

/** Час під повідомленням: «14:05», «вчора, 14:05», «03.11, 14:05» або «03.11.2025, 14:05». */
fun formatChatTime(iso: String, now: ZonedDateTime): String? {
    val time = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(now.zone) }.getOrNull() ?: return null
    val hoursMinutes = time.format(DateTimeFormatter.ofPattern("HH:mm"))
    val daysAgo = ChronoUnit.DAYS.between(time.toLocalDate(), now.toLocalDate())
    return when {
        daysAgo == 0L -> hoursMinutes
        daysAgo == 1L -> "вчора, $hoursMinutes"
        time.year == now.year -> "${time.format(DateTimeFormatter.ofPattern("dd.MM"))}, $hoursMinutes"
        else -> "${time.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}, $hoursMinutes"
    }
}

/**
 * Помилки, для яких сервер чату сам дає зрозумілий текст: MP429 — ліміт частоти
 * («Не частіше ніж одне повідомлення на 2 секунди» / «Не більше 30 повідомлень за хвилину…»),
 * 23514 — текст не пройшов CHECK. Для решти — null (тоді загальний toUserMessage).
 */
fun chatServerErrorText(code: String?, serverMessage: String?): String? = when (code) {
    "MP429" -> serverMessage?.takeIf { it.isNotBlank() } ?: "Забагато повідомлень поспіль. Зачекайте трохи"
    "23514" -> "Повідомлення має містити від 1 до $CHAT_MAX_LENGTH символів"
    else -> null
}
