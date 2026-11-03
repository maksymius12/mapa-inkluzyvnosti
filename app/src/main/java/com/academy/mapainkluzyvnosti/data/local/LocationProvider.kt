package com.academy.mapainkluzyvnosti.data.local

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Джерело локації — так само зберігається в sos_requests.location_source. */
enum class LocationSource(val apiValue: String) {
    /** Свіжа точна позиція (супутники або точний Wi-Fi), ±100 м і краще. */
    GPS("gps"),
    /** Свіжа позиція з мережі (Wi-Fi / вежі), гірша за ±100 м. */
    NETWORK("network"),
    /** Остання надійна позиція, давніша за [LocationProvider.FRESH_MS]. */
    LAST_KNOWN("last_known"),
    /** Вказана користувачем на мапі чи вибором закладу. */
    MANUAL("manual"),
    NONE("none");

    companion object {
        fun fromApi(value: String?): LocationSource = entries.firstOrNull { it.apiValue == value } ?: NONE
    }
}

data class LocationFix(
    val point: GeoPoint,
    val accuracyM: Float?,
    /** Час визначення позиції (мс з епохи). */
    val timeMs: Long,
    val source: LocationSource
)

/** Чому позицію відкинуто — для логів і підказки «Геолокація неточна». */
enum class Implausibility { MOCK, LOW_ACCURACY, OUTSIDE_KYIV, IMPOSSIBLE_JUMP }

sealed interface LocationResult {
    /** Свіжа правдоподібна позиція. */
    data class Reliable(val fix: LocationFix) : LocationResult

    /** Позицію отримано, але вона неправдоподібна (РЕБ, спуфінг, mock); [lastReliable] — що використовувати замість. */
    data class Unreliable(val reasons: Set<Implausibility>, val lastReliable: LocationFix?) : LocationResult

    /** Не вдалося визначити (немає дозволу, вимкнено геолокацію, не встигли). */
    data class Unavailable(val lastReliable: LocationFix?) : LocationResult
}

/**
 * Геолокація, що не «тупить» і не вірить РЕБ.
 *
 * Чому раніше було повільно: DeviceLocation брав лише GPS-провайдера LocationManager, якщо той увімкнений, і
 * чекав на супутниковий фікс до 8 с — у приміщенні чи під глушінням це завжди повний таймаут, а мережеве
 * визначення не пробувалось зовсім. До того ж дозвіл просили лише ACCESS_FINE_LOCATION — на Android 12+
 * без ACCESS_COARSE_LOCATION система такий запит ігнорує.
 *
 * Тепер — Fused Location (Wi-Fi, вежі, супутники разом; мережеві джерела значно менше страждають від
 * глушіння GPS), миттєва кешована позиція + короткий запит свіжої, і кожна позиція проходить перевірку
 * правдоподібності: mock, точність гірша за [MAX_ACCURACY_M], поза Києвом, фізично неможливий стрибок.
 * Ненадійна позиція ніколи не стає основною — замість неї остання надійна (зберігається між запусками).
 */
class LocationProvider(private val context: Context) {

    private val prefs = context.getSharedPreferences("last_reliable_location", Context.MODE_PRIVATE)
    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private val hasPlayServices: Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    private val _lastReliable = MutableStateFlow(loadLastReliable())
    val lastReliable: StateFlow<LocationFix?> = _lastReliable.asStateFlow()

    /** Остання спроба дала неправдоподібну позицію — мапа показує «Геолокація неточна». */
    private val _isUnreliable = MutableStateFlow(false)
    val isUnreliable: StateFlow<Boolean> = _isUnreliable.asStateFlow()

    fun hasPermission(): Boolean = hasFine() || hasCoarse()

    private fun hasFine() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasCoarse() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Найкраще, що є прямо зараз, без очікування на супутники: кешована системою позиція, якщо вона
     * свіжа й правдоподібна, інакше остання надійна (джерело [LocationSource.LAST_KNOWN]) або null.
     */
    suspend fun immediate(): LocationFix? {
        val cached = if (hasPermission()) withTimeoutOrNull(IMMEDIATE_TIMEOUT_MS) { systemLastLocation() } else null
        if (cached != null && System.currentTimeMillis() - cached.time <= FRESH_MS) {
            val result = evaluate(cached)
            if (result is LocationResult.Reliable) return result.fix
        }
        return asOfNow(_lastReliable.value)
    }

    /** Свіжа позиція (до [timeoutMs]) з перевіркою правдоподібності. */
    suspend fun current(timeoutMs: Long = CURRENT_TIMEOUT_MS): LocationResult {
        if (!hasPermission()) return LocationResult.Unavailable(asOfNow(_lastReliable.value))
        val location = withTimeoutOrNull(timeoutMs + 1_000) { freshLocation(timeoutMs) }
            ?: return LocationResult.Unavailable(asOfNow(_lastReliable.value))
        return evaluate(location)
    }

    /** Остання надійна позиція з актуальним джерелом (свіжа — як є, давня — LAST_KNOWN). */
    fun lastReliableNow(): LocationFix? = asOfNow(_lastReliable.value)

    private fun asOfNow(fix: LocationFix?): LocationFix? = when {
        fix == null -> null
        fix.source == LocationSource.MANUAL -> fix
        System.currentTimeMillis() - fix.timeMs > FRESH_MS -> fix.copy(source = LocationSource.LAST_KNOWN)
        else -> fix
    }

    private fun evaluate(location: Location): LocationResult {
        val reasons = implausibility(location, _lastReliable.value)
        return if (reasons.isEmpty()) {
            val accuracy = if (location.hasAccuracy()) location.accuracy else null
            val fix = LocationFix(
                point = GeoPoint(location.latitude, location.longitude),
                accuracyM = accuracy,
                timeMs = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                source = if (accuracy != null && accuracy <= PRECISE_ACCURACY_M) LocationSource.GPS else LocationSource.NETWORK
            )
            saveLastReliable(fix)
            _isUnreliable.value = false
            LocationResult.Reliable(fix)
        } else {
            Log.w(TAG, "Відкинуто неправдоподібну позицію: $reasons (accuracy=${location.accuracy})")
            _isUnreliable.value = true
            LocationResult.Unreliable(reasons, asOfNow(_lastReliable.value))
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun systemLastLocation(): Location? = runCatching {
        if (hasPlayServices) {
            fused.lastLocation.awaitOrNull()
        } else {
            val manager = context.getSystemService(LocationManager::class.java)
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { runCatching { manager?.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        }
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(timeoutMs: Long): Location? = runCatching {
        if (hasPlayServices) {
            // Без точного дозволу — збалансований режим (мережа); з ним — найточніший, але Fused сам бере
            // Wi-Fi / вежі, якщо супутників немає, тож під глушінням GPS відповідь усе одно прийде.
            val request = CurrentLocationRequest.Builder()
                .setPriority(if (hasFine()) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setDurationMillis(timeoutMs)
                .setMaxUpdateAgeMillis(MAX_CACHED_AGE_MS)
                .build()
            val cancellation = CancellationTokenSource()
            try {
                fused.getCurrentLocation(request, cancellation.token).awaitOrNull()
            } finally {
                cancellation.cancel()
            }
        } else {
            legacyFreshLocation(timeoutMs)
        }
    }.getOrNull()

    /** Без Google Play Services: перший фікс від мережі або GPS — хто відповість раніше. */
    @SuppressLint("MissingPermission")
    private suspend fun legacyFreshLocation(timeoutMs: Long): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            .filter { it != LocationManager.GPS_PROVIDER || hasFine() }
        if (providers.isEmpty()) return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                }
                providers.forEach { provider ->
                    runCatching {
                        manager.requestLocationUpdates(provider, 0L, 0f, listener, android.os.Looper.getMainLooper())
                    }
                }
                cont.invokeOnCancellation { manager.removeUpdates(listener) }
            }
        }
    }

    private fun saveLastReliable(fix: LocationFix) {
        _lastReliable.value = fix
        prefs.edit()
            .putString(KEY_LAT, fix.point.lat.toString())
            .putString(KEY_LNG, fix.point.lng.toString())
            .putFloat(KEY_ACCURACY, fix.accuracyM ?: -1f)
            .putLong(KEY_TIME, fix.timeMs)
            .putString(KEY_SOURCE, fix.source.apiValue)
            .apply()
    }

    private fun loadLastReliable(): LocationFix? {
        val lat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null
        val lng = prefs.getString(KEY_LNG, null)?.toDoubleOrNull() ?: return null
        val accuracy = prefs.getFloat(KEY_ACCURACY, -1f).takeIf { it >= 0f }
        return LocationFix(GeoPoint(lat, lng), accuracy, prefs.getLong(KEY_TIME, 0L), LocationSource.fromApi(prefs.getString(KEY_SOURCE, null)))
    }

    companion object {
        private const val TAG = "LocationProvider"

        /** Свіжою вважаємо позицію, молодшу за 2 хв; старша — «остання відома». */
        const val FRESH_MS = 2 * 60_000L
        const val CURRENT_TIMEOUT_MS = 8_000L
        private const val IMMEDIATE_TIMEOUT_MS = 1_500L
        private const val MAX_CACHED_AGE_MS = 30_000L

        /** Гірше за ±500 м — позиція неінформативна (часто так виглядає РЕБ чи сама лише вежа). */
        const val MAX_ACCURACY_M = 500f
        private const val PRECISE_ACCURACY_M = 100f

        /** Швидше за ~180 км/год між двома позиціями пересуватися містом неможливо — це стрибок спуфінгу. */
        private const val MAX_SPEED_M_PER_S = 50.0

        /** Київ із запасом ~5 км (спуфінг зазвичай «переносить» телефон в інше місто чи аеропорт). */
        private const val KYIV_MIN_LAT = 50.17
        private const val KYIV_MAX_LAT = 50.64
        private const val KYIV_MIN_LNG = 30.17
        private const val KYIV_MAX_LNG = 30.90

        private const val KEY_LAT = "lat"
        private const val KEY_LNG = "lng"
        private const val KEY_ACCURACY = "accuracy"
        private const val KEY_TIME = "time"
        private const val KEY_SOURCE = "source"

        fun isInsideKyiv(point: GeoPoint): Boolean =
            point.lat in KYIV_MIN_LAT..KYIV_MAX_LAT && point.lng in KYIV_MIN_LNG..KYIV_MAX_LNG

        /** Перевірка правдоподібності; порожній набір — позиції можна вірити. */
        fun implausibility(location: Location, previous: LocationFix?): Set<Implausibility> {
            val reasons = mutableSetOf<Implausibility>()
            val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                location.isMock
            } else {
                @Suppress("DEPRECATION")
                location.isFromMockProvider
            }
            if (isMock) reasons += Implausibility.MOCK
            if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) reasons += Implausibility.LOW_ACCURACY
            val point = GeoPoint(location.latitude, location.longitude)
            if (!isInsideKyiv(point)) reasons += Implausibility.OUTSIDE_KYIV
            if (previous != null && previous.source != LocationSource.MANUAL) {
                val seconds = kotlin.math.abs(location.time - previous.timeMs) / 1000.0
                val distance = FloatArray(1).also {
                    Location.distanceBetween(previous.point.lat, previous.point.lng, point.lat, point.lng, it)
                }[0]
                // Допуск на точність обох позицій, щоб дрібне «тремтіння» за секунду не вважалося стрибком.
                val tolerance = (location.accuracy.takeIf { location.hasAccuracy() } ?: 0f) + (previous.accuracyM ?: 0f)
                if ((distance - tolerance) / seconds.coerceAtLeast(1.0) > MAX_SPEED_M_PER_S) {
                    reasons += Implausibility.IMPOSSIBLE_JUMP
                }
            }
            return reasons
        }
    }
}

private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
    }
}
