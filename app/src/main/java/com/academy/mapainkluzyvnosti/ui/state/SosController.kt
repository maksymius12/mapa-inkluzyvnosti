package com.academy.mapainkluzyvnosti.ui.state

import android.content.Context
import com.academy.mapainkluzyvnosti.data.local.LocationFix
import com.academy.mapainkluzyvnosti.data.local.LocationProvider
import com.academy.mapainkluzyvnosti.data.local.LocationResult
import com.academy.mapainkluzyvnosti.data.local.LocationSource
import com.academy.mapainkluzyvnosti.data.local.NetworkMonitor
import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import com.academy.mapainkluzyvnosti.data.model.SosProblemType
import com.academy.mapainkluzyvnosti.data.remote.toUserMessage
import com.academy.mapainkluzyvnosti.data.repository.SosRepository
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException

enum class SosPhase {
    /** Запиту немає — екран показує кнопку «Утримуйте, щоб надіслати». */
    IDLE,
    SENDING,
    /** Сервер прийняв запит. */
    SENT,
    /** Не надіслано (немає мережі чи помилка сервера) — збережено й повторюється автоматично. */
    QUEUED,
    CANCELLED,
    /** Гість або сесії немає — SOS без акаунта не надіслати. */
    NEEDS_LOGIN
}

data class SosState(
    val phase: SosPhase = SosPhase.IDLE,
    val requestId: String? = null,
    val location: LocationFix? = null,
    /** Свіжа позиція виявилась неправдоподібною (РЕБ / спуфінг) — використано останню надійну. */
    val locationRejected: Boolean = false,
    val isLocating: Boolean = false,
    val problemType: SosProblemType? = null,
    val comment: String = "",
    /** Є зміни (тип, коментар, локація, скасування), які ще не дійшли до сервера. */
    val hasUnsyncedChanges: Boolean = false,
    val isCancelling: Boolean = false,
    /** Остання помилка відправки — показується разом із кнопкою 112. */
    val error: String? = null
)

/**
 * SOS живе довше за екран (Koin singleton): натискання (утримання) кнопки одразу створює запит у
 * sos_requests з найкращою локацією, яка є прямо зараз, — без проміжних екранів і без очікування GPS.
 * Далі, вже після відправки: свіжа правдоподібна позиція, тип проблеми, коментар і ручне уточнення
 * оновлюють той самий запис; «Скасувати» закриває його.
 *
 * Без мережі запит не вдає, що пішов: стан [SosPhase.QUEUED], запит зберігається на диску й
 * повторюється, щойно з'явиться зв'язок (і кожні [RETRY_MS]), а екран показує 112.
 */
class SosController(
    context: Context,
    private val sosRepository: SosRepository,
    private val locationProvider: LocationProvider,
    private val networkMonitor: NetworkMonitor,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore,
    private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("sos_pending", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(SosState())
    val state: StateFlow<SosState> = _state.asStateFlow()

    /** Що з локальних змін ще треба відправити в уже створений запит. */
    private var detailsDirty = false
    private var locationDirty = false
    private var cancelRequested = false
    private var userId: String? = null

    private var retryJob: Job? = null
    private var refineJob: Job? = null

    init {
        restorePending()
    }

    /** Утримання кнопки SOS завершено — створюємо й надсилаємо запит одразу. */
    fun trigger() {
        val phase = _state.value.phase
        if (phase == SosPhase.SENDING || phase == SosPhase.SENT || phase == SosPhase.QUEUED) return
        val user = currentUserStore.user.value
        if (user == null || demoModeStore.isDemoMode.value) {
            _state.value = SosState(phase = SosPhase.NEEDS_LOGIN)
            return
        }
        userId = user.id
        detailsDirty = false
        locationDirty = false
        cancelRequested = false
        _state.value = SosState(phase = SosPhase.SENDING, isLocating = true)
        scope.launch {
            // Лише миттєве: кешована правдоподібна позиція або остання надійна (до ~1,5 с). Нічого не чекаємо.
            val location = locationProvider.immediate()
            _state.update { it.copy(location = location) }
            savePending()
            sync()
            refineLocation()
        }
    }

    /** Що сталося (швидкий варіант) і коментар — доповнюють уже надісланий запит. */
    fun setDetails(problemType: SosProblemType?, comment: String) {
        _state.update { it.copy(problemType = problemType, comment = comment.take(COMMENT_MAX)) }
        detailsDirty = true
        afterLocalChange()
    }

    /** «Уточнити, де я»: точка на мапі чи вибраний заклад. Ручна позиція більше не перезаписується GPS. */
    fun setManualLocation(point: GeoPoint) {
        refineJob?.cancel()
        _state.update {
            it.copy(
                location = LocationFix(point, null, System.currentTimeMillis(), LocationSource.MANUAL),
                isLocating = false,
                locationRejected = false
            )
        }
        locationDirty = true
        afterLocalChange()
    }

    /**
     * Ще не на сервері — запит просто не надсилається; якщо створення вже в польоті (тримає mutex),
     * sync скасує його одразу після створення.
     */
    fun cancel() {
        val phase = _state.value.phase
        if (phase != SosPhase.SENT && phase != SosPhase.SENDING && phase != SosPhase.QUEUED) return
        cancelRequested = true
        _state.update { it.copy(isCancelling = true) }
        scope.launch { sync() }
    }

    /** Повторити зараз (кнопка на екрані помилки). */
    fun retryNow() {
        scope.launch { sync() }
    }

    /** Після скасування / входу — екран знову в початковому стані. */
    fun reset() {
        val phase = _state.value.phase
        if (phase == SosPhase.CANCELLED || phase == SosPhase.NEEDS_LOGIN) _state.value = SosState()
    }

    private fun afterLocalChange() {
        savePending()
        scope.launch { sync() }
    }

    private suspend fun sync(): Unit = mutex.withLock {
        val uid = userId ?: return
        val state = _state.value
        if (state.phase == SosPhase.CANCELLED || state.phase == SosPhase.IDLE) return
        try {
            var requestId = state.requestId
            if (requestId == null) {
                if (cancelRequested) {
                    finishCancelled()
                    return
                }
                requestId = sosRepository.createRequest(
                    userId = uid,
                    location = state.location,
                    problemType = state.problemType,
                    comment = state.comment.trim().ifBlank { null }
                )
                // Усе, що було на момент створення, уже в запиті.
                detailsDirty = false
                locationDirty = false
                _state.update { it.copy(requestId = requestId, phase = SosPhase.SENT, error = null) }
            }
            if (cancelRequested) {
                sosRepository.cancel(requestId)
                finishCancelled()
                return
            }
            if (detailsDirty) {
                val current = _state.value
                sosRepository.updateDetails(requestId, current.problemType, current.comment.trim().ifBlank { null })
                detailsDirty = false
            }
            if (locationDirty) {
                _state.value.location?.let { sosRepository.updateLocation(requestId, it) }
                locationDirty = false
            }
            _state.update { it.copy(phase = SosPhase.SENT, hasUnsyncedChanges = false, error = null) }
            clearPending()
            retryJob?.cancel()
        } catch (e: Exception) {
            val notSentYet = _state.value.requestId == null
            _state.update {
                it.copy(
                    phase = if (notSentYet) SosPhase.QUEUED else SosPhase.SENT,
                    hasUnsyncedChanges = !notSentYet,
                    isCancelling = it.isCancelling && !e.isNetwork(),
                    error = if (e.isNetwork()) NO_NETWORK else e.toUserMessage("Сервер не прийняв запит")
                )
            }
            scheduleRetry()
        }
    }

    private fun finishCancelled() {
        refineJob?.cancel()
        retryJob?.cancel()
        clearPending()
        cancelRequested = false
        _state.value = SosState(phase = SosPhase.CANCELLED)
    }

    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            while (true) {
                // Або зв'язок відновився, або минув інтервал — що раніше.
                withTimeoutOrNull(RETRY_MS) { networkMonitor.reconnected.first() }
                sync()
                val s = _state.value
                if (s.phase != SosPhase.QUEUED && !s.hasUnsyncedChanges) break
                delay(1_000)
            }
        }
    }

    /** Після відправки шукаємо свіжу позицію; ненадійну (РЕБ, спуфінг) не використовуємо. */
    private fun refineLocation() {
        refineJob?.cancel()
        refineJob = scope.launch {
            _state.update { it.copy(isLocating = true) }
            val result = locationProvider.current(REFINE_TIMEOUT_MS)
            _state.update { it.copy(isLocating = false) }
            when (result) {
                is LocationResult.Reliable -> {
                    val current = _state.value.location
                    if (current?.source == LocationSource.MANUAL) return@launch
                    val better = current == null ||
                        current.source == LocationSource.LAST_KNOWN ||
                        (result.fix.accuracyM ?: Float.MAX_VALUE) < (current.accuracyM ?: Float.MAX_VALUE) ||
                        result.fix.timeMs > current.timeMs
                    if (better) {
                        _state.update { it.copy(location = result.fix, locationRejected = false) }
                        locationDirty = true
                        afterLocalChange()
                    }
                }
                is LocationResult.Unreliable -> _state.update { it.copy(locationRejected = true) }
                is LocationResult.Unavailable -> Unit
            }
        }
    }

    // ---- Незавершений (ще не прийнятий сервером) запит переживає закриття застосунку ----

    private fun savePending() {
        val s = _state.value
        val uid = userId ?: return
        if (s.requestId != null) return
        val json = JSONObject()
            .put("user_id", uid)
            .put("created_ms", System.currentTimeMillis())
            .put("problem", s.problemType?.name)
            .put("comment", s.comment)
        s.location?.let {
            json.put("lat", it.point.lat).put("lng", it.point.lng).put("time", it.timeMs).put("source", it.source.apiValue)
            it.accuracyM?.let { a -> json.put("accuracy", a.toDouble()) }
        }
        prefs.edit().putString(KEY_PENDING, json.toString()).apply()
    }

    private fun clearPending() {
        prefs.edit().remove(KEY_PENDING).apply()
    }

    private fun restorePending() {
        val raw = prefs.getString(KEY_PENDING, null) ?: return
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return clearPending()
        if (System.currentTimeMillis() - json.optLong("created_ms") > PENDING_MAX_AGE_MS) return clearPending()
        userId = json.optString("user_id").takeIf { it.isNotBlank() } ?: return clearPending()
        val location = if (json.has("lat")) {
            LocationFix(
                point = GeoPoint(json.getDouble("lat"), json.getDouble("lng")),
                accuracyM = if (json.has("accuracy")) json.getDouble("accuracy").toFloat() else null,
                timeMs = json.optLong("time"),
                source = LocationSource.fromApi(json.optString("source"))
            )
        } else {
            null
        }
        _state.value = SosState(
            phase = SosPhase.QUEUED,
            location = location,
            problemType = json.optString("problem").takeIf { it.isNotBlank() && it != "null" }
                ?.let { name -> SosProblemType.entries.firstOrNull { it.name == name } },
            comment = json.optString("comment"),
            error = NO_NETWORK
        )
        scope.launch { sync() }
    }

    private fun Throwable.isNetwork(): Boolean =
        this is IOException || this is HttpRequestException || this is HttpRequestTimeoutException ||
            cause is IOException

    companion object {
        private const val KEY_PENDING = "pending"
        private const val RETRY_MS = 15_000L
        private const val REFINE_TIMEOUT_MS = 20_000L
        private const val PENDING_MAX_AGE_MS = 2 * 60 * 60_000L
        const val COMMENT_MAX = 500
        const val NO_NETWORK = "Немає зв'язку з інтернетом"
    }
}
