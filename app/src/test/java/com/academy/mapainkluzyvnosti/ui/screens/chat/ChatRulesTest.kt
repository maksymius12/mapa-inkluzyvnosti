package com.academy.mapainkluzyvnosti.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ChatRulesTest {

    private val kyiv = ZoneId.of("Europe/Kyiv")
    private val now = ZonedDateTime.of(2026, 11, 11, 18, 30, 0, 0, kyiv)

    @Test
    fun `text is trimmed before sending`() {
        assertEquals("Привіт", normalizeChatText("  \n Привіт \t\n"))
        assertNull(validateChatText("  Привіт  "))
    }

    @Test
    fun `empty and whitespace-only text cannot be sent`() {
        assertEquals(ChatTextProblem.EMPTY, validateChatText(""))
        assertEquals(ChatTextProblem.EMPTY, validateChatText("   \n\t  "))
        // Нерозривний пробіл теж обрізається (Kotlin trim), тож порожнім на сервер не піде.
        assertEquals(ChatTextProblem.EMPTY, validateChatText("   "))
    }

    @Test
    fun `500 characters is the limit, 501 is too long`() {
        assertNull(validateChatText("я".repeat(500)))
        assertEquals(ChatTextProblem.TOO_LONG, validateChatText("я".repeat(501)))
    }

    @Test
    fun `spaces around do not count toward the limit`() {
        assertNull(validateChatText("  " + "a".repeat(500) + "  "))
    }

    @Test
    fun `emoji counts as one character like postgres char_length`() {
        val emoji500 = "😀".repeat(500)
        assertEquals(1000, emoji500.length)
        assertEquals(500, chatTextLength(emoji500))
        assertNull(validateChatText(emoji500))
        assertEquals(ChatTextProblem.TOO_LONG, validateChatText(emoji500 + "😀"))
    }

    @Test
    fun `counter appears only near the limit`() {
        assertNull(chatCounterText("a".repeat(CHAT_COUNTER_FROM - 1)))
        assertEquals("400/500", chatCounterText("a".repeat(400)))
        assertEquals("501/500", chatCounterText("a".repeat(501)))
    }

    @Test
    fun `author deletes own, admin deletes any, others cannot`() {
        assertTrue(canDeleteChatMessage(isMine = true, isAdmin = false))
        assertTrue(canDeleteChatMessage(isMine = false, isAdmin = true))
        assertTrue(canDeleteChatMessage(isMine = true, isAdmin = true))
        assertFalse(canDeleteChatMessage(isMine = false, isAdmin = false))
    }

    @Test
    fun `time today is shown as hours and minutes in local zone`() {
        // 14:05 UTC = 16:05 у Києві (UTC+2 у листопаді).
        assertEquals("16:05", formatChatTime("2026-11-11T14:05:31.123456+00:00", now))
    }

    @Test
    fun `time yesterday and earlier`() {
        assertEquals("вчора, 23:10", formatChatTime("2026-11-10T21:10:00+00:00", now))
        assertEquals("03.11, 09:05", formatChatTime("2026-11-03T07:05:00Z", now))
        assertEquals("30.12.2025, 12:00", formatChatTime("2025-12-30T10:00:00+00:00", now))
    }

    @Test
    fun `unparseable time is hidden`() {
        assertNull(formatChatTime("not a date", now))
    }

    @Test
    fun `server rate limit text is shown as is`() {
        assertEquals(
            "Не частіше ніж одне повідомлення на 2 секунди",
            chatServerErrorText("MP429", "Не частіше ніж одне повідомлення на 2 секунди")
        )
        assertEquals("Забагато повідомлень поспіль. Зачекайте трохи", chatServerErrorText("MP429", ""))
        assertEquals("Повідомлення має містити від 1 до 500 символів", chatServerErrorText("23514", "violates check"))
        assertNull(chatServerErrorText("42501", "permission denied"))
        assertNull(chatServerErrorText(null, null))
    }
}
