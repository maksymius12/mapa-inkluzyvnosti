package com.academy.mapainkluzyvnosti

import android.app.Application
import com.academy.mapainkluzyvnosti.data.local.SavedRoutesStorage
import com.academy.mapainkluzyvnosti.data.local.SettingsPreferences
import com.academy.mapainkluzyvnosti.di.appModules
import com.academy.mapainkluzyvnosti.notifications.AppNotifications
import com.academy.mapainkluzyvnosti.work.RouteCheckScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.maplibre.android.MapLibre

class MapaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@MapaApplication)
            modules(appModules)
        }
        MapLibre.getInstance(this)
        AppNotifications.createChannels(this)
        val routeNotificationsEnabled = SettingsPreferences.load(this).routeNotificationsEnabled
        if (routeNotificationsEnabled && SavedRoutesStorage.load(this).isNotEmpty()) {
            RouteCheckScheduler.scheduleDaily(this)
        }
    }
}
