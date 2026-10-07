package com.academy.mapainkluzyvnosti.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Щоденна ранкова перевірка збережених маршрутів на нові бар'єри.
 * Планується лише коли в застосунку є хоч один збережений маршрут — інакше
 * запускати фонову задачу немає сенсу (навіщо витрачати ресурс пристрою).
 */
object RouteCheckScheduler {

    private const val UNIQUE_WORK_NAME = "morning_route_barrier_check"
    private const val MORNING_HOUR = 7

    fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<RouteBarrierCheckWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNextMorning(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun millisUntilNextMorning(): Long {
        val now = Calendar.getInstance()
        val nextRun = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, MORNING_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return nextRun.timeInMillis - now.timeInMillis
    }
}
