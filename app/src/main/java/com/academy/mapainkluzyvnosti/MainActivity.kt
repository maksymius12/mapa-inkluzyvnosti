package com.academy.mapainkluzyvnosti

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.academy.mapainkluzyvnosti.ui.navigation.MapaNavHost
import com.academy.mapainkluzyvnosti.ui.state.SettingsStore
import com.academy.mapainkluzyvnosti.ui.state.ThemeStore
import com.academy.mapainkluzyvnosti.ui.theme.MapaInkluzyvnostiTheme
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            val themeStore = koinInject<ThemeStore>()
            val isDarkTheme by themeStore.isDarkTheme.collectAsState()
            val settingsStore = koinInject<SettingsStore>()
            val settings by settingsStore.settings.collectAsState()
            MapaInkluzyvnostiTheme(darkTheme = isDarkTheme, largeText = settings.isLargeText) {
                MapaNavHost()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
