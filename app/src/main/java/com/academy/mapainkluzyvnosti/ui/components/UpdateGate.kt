package com.academy.mapainkluzyvnosti.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.academy.mapainkluzyvnosti.BuildConfig
import com.academy.mapainkluzyvnosti.data.remote.AppVersionChecker
import com.academy.mapainkluzyvnosti.data.remote.UpdateRequirement
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Обгортка над усім застосунком: якщо збірка старша за app_config.min_version_code, замість
 * застосунку — повноекранне «Потрібне оновлення» (жоден екран не вантажить дані, тож і помилок
 * «Недостатньо прав» немає). Перевірка — на старті й щоразу, коли застосунок повертається на екран.
 * Поки триває перша перевірка (до ~4 с), показуємо лише фон; сервер недоступний — застосунок як є.
 */
@Composable
fun UpdateGate(background: Color, content: @Composable () -> Unit) {
    val checker = koinInject<AppVersionChecker>()
    val requirement by checker.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) scope.launch { checker.refresh() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val state = requirement) {
        UpdateRequirement.Checking -> Box(Modifier.fillMaxSize().background(background))
        is UpdateRequirement.Required -> UpdateRequiredScreen(state)
        UpdateRequirement.Unknown, UpdateRequirement.UpToDate -> content()
    }
}

@Composable
fun UpdateRequiredScreen(state: UpdateRequirement.Required, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { paneTitle = "Потрібне оновлення" }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
            Text(
                text = "Потрібне оновлення",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 32.dp)
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Assertive
                    }
            )
            Text(
                text = "Ця версія застосунку більше не працює з сервером. Встановіть нову версію — " +
                    "ваші дані, місця й перевірки збережені на сервері й нікуди не зникнуть.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = buildString {
                    append("Встановлено: ").append(BuildConfig.VERSION_NAME)
                    state.latestVersionName?.let { append(" · Нова: ").append(it) }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
            PrimaryButton(
                text = "Завантажити оновлення",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.downloadUrl))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        // Немає браузера — кнопка нічого не робить; посилання видно нижче.
                    }
                },
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                text = state.downloadUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
