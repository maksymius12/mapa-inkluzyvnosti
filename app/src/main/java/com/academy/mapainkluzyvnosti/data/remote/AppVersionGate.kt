package com.academy.mapainkluzyvnosti.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Примусове оновлення: таблиця public.app_config (міграція 20261111120000_app_config.sql).
 *
 * КОНТРАКТ — не ламати (див. CLAUDE.md): ця перевірка має працювати в кожній опублікованій збірці.
 * Тому вона навмисно незалежна від решти коду:
 *  * звичайний HTTP-запит (без supabase-kt і без сесії користувача — лише publishable-ключ);
 *  * читає рівно три колонки за назвою, нові колонки в app_config її не зачіпають;
 *  * будь-яка помилка (немає мережі, таймаут, 4xx/5xx, інший JSON) — «невідомо», застосунок працює як є.
 */
@Serializable
data class AppConfig(
    @SerialName("min_version_code") val minVersionCode: Int? = null,
    @SerialName("latest_version_name") val latestVersionName: String? = null,
    @SerialName("apk_url") val apkUrl: String? = null
)

sealed interface UpdateRequirement {
    /** Перша перевірка ще триває (короткі таймаути — не довше кількох секунд). */
    data object Checking : UpdateRequirement

    /** Сервер недоступний або відповідь незрозуміла — нічого не блокуємо. */
    data object Unknown : UpdateRequirement

    data object UpToDate : UpdateRequirement

    data class Required(val latestVersionName: String?, val downloadUrl: String) : UpdateRequirement
}

object AppVersionPolicy {

    /** Куди вести, якщо в app_config не вказано (або вказано не https) посилання. */
    const val DEFAULT_DOWNLOAD_URL = "https://github.com/maksymius12/mapa-inkluzyvnosti/releases/latest"

    fun evaluate(currentVersionCode: Int, config: AppConfig?): UpdateRequirement {
        val min = config?.minVersionCode ?: return UpdateRequirement.Unknown
        if (currentVersionCode >= min) return UpdateRequirement.UpToDate
        val url = config.apkUrl?.trim()?.takeIf { it.startsWith("https://") } ?: DEFAULT_DOWNLOAD_URL
        return UpdateRequirement.Required(config.latestVersionName?.trim()?.takeIf { it.isNotEmpty() }, url)
    }

    /** Відповідь PostgREST — масив рядків; беремо перший. Будь-що інше — null. */
    fun parse(body: String): AppConfig? = runCatching {
        json.decodeFromString<List<AppConfig>>(body).firstOrNull()
    }.getOrNull()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
}

class AppVersionChecker(
    private val supabaseUrl: String,
    private val apiKey: String,
    private val currentVersionCode: Int
) {
    private val _state = MutableStateFlow<UpdateRequirement>(UpdateRequirement.Checking)
    val state: StateFlow<UpdateRequirement> = _state.asStateFlow()

    /** Викликати на старті й при поверненні в застосунок. */
    suspend fun refresh() {
        val result = AppVersionPolicy.evaluate(currentVersionCode, fetchConfig())
        _state.update { previous ->
            // Якщо вже відомо, що збірка застаріла, тимчасова відсутність мережі цього не скасовує.
            if (result == UpdateRequirement.Unknown && previous is UpdateRequirement.Required) previous else result
        }
    }

    private suspend fun fetchConfig(): AppConfig? = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || apiKey.isBlank()) return@withContext null
        runCatching {
            val url = URL("${supabaseUrl.trimEnd('/')}/rest/v1/app_config?select=$COLUMNS&limit=1")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                AppVersionPolicy.parse(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private companion object {
        const val COLUMNS = "min_version_code,latest_version_name,apk_url"
        const val TIMEOUT_MS = 4000
    }
}
