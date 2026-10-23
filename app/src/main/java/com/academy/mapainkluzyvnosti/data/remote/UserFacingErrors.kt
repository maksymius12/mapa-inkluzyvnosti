package com.academy.mapainkluzyvnosti.data.remote

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException

/**
 * Перетворює помилку мережі чи сервера на зрозумілий українською текст без технічних деталей.
 * Коди MPxxx — власні SQLSTATE з тригерів міграції 20261023120000_user_places.sql.
 */
fun Throwable.toUserMessage(fallback: String): String = when (this) {
    is PostgrestRestException -> when (code) {
        "MP400" -> error.takeIf { it.isNotBlank() } ?: "Перевірте правильність заповнення полів"
        "MP401" -> "Увійдіть, щоб виконати цю дію"
        "MP403", "42501" -> "Недостатньо прав для цієї дії"
        "MP404" -> "Місце за межами району"
        "MP429" -> "Можна додати не більше 10 місць за добу. Спробуйте завтра"
        "23505" -> "Ви вже поскаржилися на це місце"
        "PGRST301", "PGRST302" -> "Сесія завершилась. Увійдіть знову"
        else -> fallback
    }
    is UnauthorizedRestException -> "Сесія завершилась. Увійдіть знову"
    is RestException -> fallback
    is HttpRequestTimeoutException -> "Сервер не відповідає. Спробуйте пізніше"
    is HttpRequestException, is IOException -> "Немає з'єднання з інтернетом. Перевірте мережу та спробуйте ще раз"
    else -> fallback
}
