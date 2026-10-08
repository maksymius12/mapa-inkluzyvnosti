package com.academy.mapainkluzyvnosti

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.academy.mapainkluzyvnosti.ui.navigation.MapaNavHost
import com.academy.mapainkluzyvnosti.ui.state.AppStartup
import com.academy.mapainkluzyvnosti.ui.state.SettingsStore
import com.academy.mapainkluzyvnosti.ui.state.ThemeStore
import com.academy.mapainkluzyvnosti.ui.theme.Background
import androidx.compose.ui.graphics.Color as ComposeColor
import com.academy.mapainkluzyvnosti.ui.theme.LightBackground
import com.academy.mapainkluzyvnosti.ui.theme.MapaInkluzyvnostiTheme
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        requestNotificationPermissionIfNeeded()
        setContent {
            val themeStore = koinInject<ThemeStore>()
            val isDarkTheme by themeStore.isDarkTheme.collectAsState()
            val settingsStore = koinInject<SettingsStore>()
            val settings by settingsStore.settings.collectAsState()
            val startup = koinInject<AppStartup>()

            // Іконки системних панелей мають читатися на фоні обраної теми застосунку, а не системної.
            LaunchedEffect(isDarkTheme) {
                val style = if (isDarkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            // Стартовий екран залежить від першого запуску, гостя й збереженої сесії Supabase.
            var startRoute by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) { startRoute = startup.resolveStartRoute() }

            MapaInkluzyvnostiTheme(darkTheme = isDarkTheme, largeText = settings.isLargeText) {
                val route = startRoute
                if (route == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isDarkTheme) ComposeColor(Background.value) else ComposeColor(LightBackground.value))
                    )
                } else {
                    MapaNavHost(startRoute = route)
                }
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
