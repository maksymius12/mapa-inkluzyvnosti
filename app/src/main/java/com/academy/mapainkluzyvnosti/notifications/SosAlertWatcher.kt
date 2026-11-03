package com.academy.mapainkluzyvnosti.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.academy.mapainkluzyvnosti.MainActivity
import com.academy.mapainkluzyvnosti.R
import com.academy.mapainkluzyvnosti.data.local.LocationProvider
import com.academy.mapainkluzyvnosti.data.repository.SosRepository
import com.academy.mapainkluzyvnosti.ui.components.locationLabel
import com.academy.mapainkluzyvnosti.ui.components.parseInstantMs
import com.academy.mapainkluzyvnosti.ui.components.titleOrDefault
import com.academy.mapainkluzyvnosti.ui.state.CurrentUserStore
import com.academy.mapainkluzyvnosti.ui.state.DemoModeStore
import com.academy.mapainkluzyvnosti.ui.state.ModerationCountsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Сповіщення волонтеру / учню / адміну про новий SOS, поки застосунок відкритий (на передньому плані).
 *
 * Це НЕ push: справжній push (FCM) у проєкті не налаштований — немає Firebase-проєкту,
 * google-services.json, секрету FCM_SERVICE_ACCOUNT і тригера на sos_requests. Тож коли застосунок
 * закритий, рецензент про SOS не дізнається; тут — лише опитування кожні [POLL_MS], поки він відкритий.
 * Заодно оновлює лічильники модерації (бейджі) при поверненні в застосунок.
 */
class SosAlertWatcher(
    private val context: Context,
    private val sosRepository: SosRepository,
    private val currentUserStore: CurrentUserStore,
    private val demoModeStore: DemoModeStore,
    private val locationProvider: LocationProvider,
    private val moderationCountsStore: ModerationCountsStore,
    private val scope: CoroutineScope
) : DefaultLifecycleObserver {

    private val prefs = context.getSharedPreferences("sos_alerts", Context.MODE_PRIVATE)
    private var job: Job? = null

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        moderationCountsStore.refresh()
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                runCatching { check() }.onFailure { e -> Log.w(TAG, "Перевірка нових SOS не вдалася", e) }
                delay(POLL_MS)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        job?.cancel()
        job = null
    }

    private suspend fun check() {
        val user = currentUserStore.user.value ?: return
        if (!user.isReviewer || demoModeStore.isDemoMode.value) return
        val notified = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
        val now = System.currentTimeMillis()
        val fresh = sosRepository.getOpenRequests().filter { sos ->
            sos.userId != user.id && sos.id !in notified &&
                (sos.createdAt?.let(::parseInstantMs)?.let { now - it <= FRESH_MS } ?: false)
        }
        if (fresh.isEmpty()) return
        val me = locationProvider.lastReliableNow()?.point
        fresh.forEach { sos ->
            val distance = if (me != null && sos.point != null) {
                val result = FloatArray(1)
                android.location.Location.distanceBetween(me.lat, me.lng, sos.point!!.lat, sos.point!!.lng, result)
                " · ≈ ${(result[0] / 100).toInt() * 100} м від вас"
            } else {
                ""
            }
            notify(sos.id, sos.problemType.titleOrDefault(), sos.locationLabel() + distance)
        }
        // Зберігаємо лише свіжі id, щоб набір не ріс безмежно.
        prefs.edit().putStringSet(KEY_NOTIFIED, (notified + fresh.map { it.id }).toList().takeLast(200).toSet()).apply()
    }

    private fun notify(id: String, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pending = PendingIntent.getActivity(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, AppNotifications.SOS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SOS поруч: $title")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
    }

    private companion object {
        const val TAG = "SosAlertWatcher"
        const val POLL_MS = 20_000L
        /** Про запити, старші за 30 хв, уже не сповіщаємо (вони й так на мапі). */
        const val FRESH_MS = 30 * 60_000L
        const val KEY_NOTIFIED = "notified_ids"
    }
}
