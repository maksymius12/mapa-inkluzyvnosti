package com.academy.mapainkluzyvnosti.data.remote

import android.util.Log
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException

/**
 * Пише в logcat справжню причину відмови Supabase: HTTP-код, code, message, details, hint
 * і поля запиту. Навмисно не логує e.message цілком — там URL і заголовки запиту.
 * Фільтр у logcat: тег «SupabaseError».
 */
object SupabaseErrorLog {
    private const val TAG = "SupabaseError"

    fun log(operation: String, error: Throwable, fields: Map<String, Any?> = emptyMap()) {
        val reason = when (error) {
            is PostgrestRestException ->
                "HTTP ${error.statusCode} code=${error.code} message=${error.error} details=${error.details} hint=${error.hint}"
            is RestException -> "HTTP ${error.statusCode} error=${error.error}"
            else -> "${error::class.java.simpleName}: ${error.message?.take(200)}"
        }
        val payload = fields.entries.joinToString(", ") { (k, v) -> "$k=$v" }
        Log.e(TAG, "$operation -> $reason; fields: {$payload}")
    }
}

/** Оновлення не зачепило жодного рядка: місця немає, його приховано/змінено або немає прав (RLS). */
class NothingUpdatedException(operation: String) : Exception("$operation: 0 rows updated")
