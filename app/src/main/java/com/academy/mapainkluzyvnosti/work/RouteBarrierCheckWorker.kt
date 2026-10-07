package com.academy.mapainkluzyvnosti.work

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.academy.mapainkluzyvnosti.MainActivity
import com.academy.mapainkluzyvnosti.R
import com.academy.mapainkluzyvnosti.data.local.SavedRoutesStorage
import com.academy.mapainkluzyvnosti.data.model.Route
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository
import com.academy.mapainkluzyvnosti.domain.usecase.FindBarriersOnRoute
import com.academy.mapainkluzyvnosti.notifications.AppNotifications
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Щоранку (див. [RouteCheckScheduler]) заново прораховує бар'єри для кожного
 * збереженого маршруту й порівнює зі станом, зафіксованим при попередній
 * перевірці. Не виконує жодних мережевих запитів, якщо збережених маршрутів
 * немає — перевірка виходить одразу.
 */
class RouteBarrierCheckWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val placeRepository: PlaceRepository by inject()

    override suspend fun doWork(): Result {
        val savedRoutes = SavedRoutesStorage.load(applicationContext)
        if (savedRoutes.isEmpty()) return Result.success()

        return try {
            val places = placeRepository.getAllPlaces()
            val updatedRoutes = savedRoutes.map { route ->
                val currentBarrierIds = FindBarriersOnRoute(route, places)
                val newBarriers = currentBarrierIds.toSet() - route.barrierPlaceIds.toSet()
                if (newBarriers.isNotEmpty()) notifyNewBarrier(route)
                route.copy(barrierPlaceIds = currentBarrierIds)
            }
            SavedRoutesStorage.save(applicationContext, updatedRoutes)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notifyNewBarrier(route: Route) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val contentIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            route.id.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, AppNotifications.ROUTE_BARRIER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${route.fromName} → ${route.toName}")
            .setContentText("На твоєму маршруті на сьогодні з'явилась перешкода — перевір деталі")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(route.id.hashCode(), notification)
    }
}
