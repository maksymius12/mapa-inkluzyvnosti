package com.academy.mapainkluzyvnosti

import android.app.Application
import com.academy.mapainkluzyvnosti.data.local.SavedRoutesStorage
import com.academy.mapainkluzyvnosti.data.local.SettingsPreferences
import com.academy.mapainkluzyvnosti.di.appModules
import com.academy.mapainkluzyvnosti.notifications.AppNotifications
import com.academy.mapainkluzyvnosti.notifications.SosAlertWatcher
import com.academy.mapainkluzyvnosti.ui.state.SessionSync
import com.academy.mapainkluzyvnosti.ui.state.WhatsNewStore
import com.academy.mapainkluzyvnosti.work.RouteCheckScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
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
        // Профіль користувача тримається в синхроні із сесією Supabase (відновлення, оновлення токена, вихід).
        GlobalContext.get().get<SessionSync>().start()
        MapLibre.getInstance(this)
        // Свіже встановлення одразу записує версію; оновлення — позначає екран «Що нового» до показу.
        GlobalContext.get().get<WhatsNewStore>().checkOnLaunch()
        AppNotifications.createChannels(this)
        GlobalContext.get().get<SosAlertWatcher>().start()
        val routeNotificationsEnabled = SettingsPreferences.load(this).routeNotificationsEnabled
        if (routeNotificationsEnabled && SavedRoutesStorage.load(this).isNotEmpty()) {
            RouteCheckScheduler.scheduleDaily(this)
        }
    }
}
