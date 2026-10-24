package com.academy.mapainkluzyvnosti.data.remote

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException

private const val TOO_MANY_ATTEMPTS = "Забагато спроб. Зачекайте кілька хвилин і спробуйте знову"
private const val SESSION_EXPIRED = "Сесія завершилась. Увійдіть знову"
const val EMAIL_ALREADY_REGISTERED = "Ця пошта вже зареєстрована. Увійдіть або відновіть пароль"

/** Supabase повертає однаковий код otp_expired і для невірного, і для простроченого коду. */
const val OTP_INVALID_OR_EXPIRED = "Невірний або прострочений код. Перевірте цифри або надішліть новий код"
const val OTP_EXPIRED = "Термін дії коду минув. Надішліть новий код"

/** Таймаут, UnknownHost, обрив з'єднання — для користувача це одне: немає інтернету. */
const val NO_CONNECTION = "Немає з'єднання з інтернетом. Перевірте мережу та спробуйте ще раз"

/**
 * Перетворює помилку мережі чи сервера на зрозумілий українською текст без технічних деталей.
 * Коди MPxxx — власні SQLSTATE з тригерів міграції 20261023120000_user_places.sql.
 */
fun Throwable.toUserMessage(fallback: String): String = when (this) {
    is AuthWeakPasswordException -> "Пароль надто простий. Зробіть його довшим і додайте літери та цифри"
    is AuthRestException -> authMessage(fallback)
    is PostgrestRestException -> when (code) {
        "MP400" -> error.takeIf { it.isNotBlank() } ?: "Перевірте правильність заповнення полів"
        "MP401" -> "Увійдіть, щоб виконати цю дію"
        "MP403", "42501" -> "Недостатньо прав для цієї дії"
        "MP404" -> "Місце за межами району"
        "MP429" -> "Можна додати не більше 10 місць за добу. Спробуйте завтра"
        "23505" -> "Ви вже поскаржилися на це місце"
        "PGRST301", "PGRST302" -> SESSION_EXPIRED
        else -> if (statusCode == 429) TOO_MANY_ATTEMPTS else fallback
    }
    is UnauthorizedRestException -> SESSION_EXPIRED
    is RestException -> if (statusCode == 429) TOO_MANY_ATTEMPTS else fallback
    is HttpRequestTimeoutException, is HttpRequestException, is IOException -> NO_CONNECTION
    else -> fallback
}

/** «Не вдалося зберегти зміни» + зрозуміла причина (доступ, мережа, місце вже змінено). */
fun Throwable.toSaveErrorMessage(): String {
    val reason = when (this) {
        is NothingUpdatedException -> "місце вже змінено чи видалено або у вас немає доступу"
        is PostgrestRestException -> when {
            code == "MP400" -> error.takeIf { it.isNotBlank() }?.replaceFirstChar { it.lowercase() } ?: "перевірте поля"
            code == "MP404" -> "місце за межами району"
            code == "PGRST301" || code == "PGRST302" -> "сесія завершилась, увійдіть знову"
            code == "PGRST204" || code == "PGRST202" -> "сервер ще не оновлено, спробуйте пізніше"
            code == "MP401" || code == "MP403" || code == "42501" || statusCode == 401 || statusCode == 403 -> "немає доступу"
            else -> "спробуйте ще раз"
        }
        is UnauthorizedRestException -> "сесія завершилась, увійдіть знову"
        is RestException -> if (statusCode == 403) "немає доступу" else "спробуйте ще раз"
        is HttpRequestTimeoutException, is HttpRequestException, is IOException -> "немає з'єднання з інтернетом"
        else -> "спробуйте ще раз"
    }
    return "Не вдалося зберегти зміни: $reason"
}

private fun AuthRestException.authMessage(fallback: String): String = when (errorCode) {
    AuthErrorCode.InvalidCredentials -> "Невірний email або пароль"
    AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists, AuthErrorCode.IdentityAlreadyExists -> EMAIL_ALREADY_REGISTERED
    AuthErrorCode.WeakPassword -> "Пароль надто простий. Зробіть його довшим і додайте літери та цифри"
    AuthErrorCode.SamePassword -> "Новий пароль має відрізнятися від попереднього"
    AuthErrorCode.EmailNotConfirmed -> "Пошту ще не підтверджено. Введіть код із листа"
    AuthErrorCode.EmailAddressInvalid, AuthErrorCode.EmailAddressNotAuthorized -> "Перевірте правильність email"
    AuthErrorCode.UserNotFound -> "Акаунта з таким email не знайдено"
    AuthErrorCode.UserBanned -> "Цей акаунт заблоковано"
    AuthErrorCode.SignupDisabled, AuthErrorCode.EmailProviderDisabled -> "Реєстрацію тимчасово вимкнено. Спробуйте пізніше"
    AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> TOO_MANY_ATTEMPTS
    AuthErrorCode.SessionNotFound, AuthErrorCode.SessionExpired,
    AuthErrorCode.RefreshTokenNotFound, AuthErrorCode.RefreshTokenAlreadyUsed, AuthErrorCode.BadJwt -> SESSION_EXPIRED
    AuthErrorCode.OtpExpired -> OTP_INVALID_OR_EXPIRED
    AuthErrorCode.RequestTimeout -> "Сервер не відповідає. Спробуйте пізніше"
    else -> if (statusCode == 429) TOO_MANY_ATTEMPTS else fallback
}
