package com.academy.mapainkluzyvnosti.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object AppNotifications {

    const val ROUTE_BARRIER_CHANNEL_ID = "route_barriers"
    const val SOS_CHANNEL_ID = "sos_alerts"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                ROUTE_BARRIER_CHANNEL_ID,
                "Оновлення маршрутів",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Сповіщення про нові бар'єри на збережених маршрутах" }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                SOS_CHANNEL_ID,
                "SOS-запити волонтерам",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Запити про допомогу поруч від інших користувачів" }
        )
    }
}
