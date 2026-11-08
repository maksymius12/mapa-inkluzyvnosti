package com.academy.mapainkluzyvnosti

import android.app.Application
import com.academy.mapainkluzyvnosti.data.local.SavedRoutesStorage
import com.academy.mapainkluzyvnosti.data.local.SettingsPreferences
import com.academy.mapainkluzyvnosti.di.appModules
import com.academy.mapainkluzyvnosti.notifications.AppNotifications
import com.academy.mapainkluzyvnosti.ui.state.ModerationCountsStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
        SettingsPreferences.clearLegacySos(this)
        AppNotifications.createChannels(this)
        // Повернулися в застосунок — свіжі лічильники «чекає розгляду» (бейджі в профілі).
        val moderationCounts = GlobalContext.get().get<ModerationCountsStore>()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = moderationCounts.refresh()
        })
        val routeNotificationsEnabled = SettingsPreferences.load(this).routeNotificationsEnabled
        if (routeNotificationsEnabled && SavedRoutesStorage.load(this).isNotEmpty()) {
            RouteCheckScheduler.scheduleDaily(this)
        }
    }
}
