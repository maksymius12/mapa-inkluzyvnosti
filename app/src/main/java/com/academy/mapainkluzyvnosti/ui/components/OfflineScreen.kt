package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.local.NetworkMonitor
import com.academy.mapainkluzyvnosti.ui.theme.AccentPrimary
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val SHOW_DELAY_MS = 1000L
private const val MIN_CHECK_MS = 600L

/**
 * Обгортка над усією навігацією: поверх неї показує екран «Ви не в мережі», коли зникає перевірений
 * інтернет (із затримкою ~1 с, щоб не мерехтіти при коротких обривах), і ховає його з повідомленням
 * «З'єднання відновлено», щойно мережа повертається. Системна «Назад» не перехоплюється — вона
 * працює для екрана під накладкою (повернення назад або вихід із застосунку).
 */
@Composable
fun NetworkAwareContent(content: @Composable () -> Unit) {
    val monitor = koinInject<NetworkMonitor>()
    val online by monitor.isOnline.collectAsState()
    var showOffline by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var stillOffline by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(online) {
        if (!online) {
            delay(SHOW_DELAY_MS)
            stillOffline = false
            showOffline = true
        } else if (showOffline) {
            showOffline = false
            snackbarHostState.showSnackbar("З'єднання відновлено")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Поки накладка видима, TalkBack не ходить по вмісту під нею.
        Box(modifier = Modifier.fillMaxSize().then(if (showOffline) Modifier.clearAndSetSemantics { } else Modifier)) {
            content()
        }
        AnimatedVisibility(visible = showOffline, enter = fadeIn(), exit = fadeOut()) {
            OfflineScreen(
                isChecking = isChecking,
                stillOffline = stillOffline,
                onRetry = {
                    if (!isChecking) {
                        scope.launch {
                            isChecking = true
                            stillOffline = false
                            delay(MIN_CHECK_MS) // щоб індикатор встиг бути помітним
                            stillOffline = !monitor.checkNow()
                            isChecking = false
                        }
                    }
                }
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp)
        )
    }
}

@Composable
fun OfflineScreen(isChecking: Boolean, stillOffline: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val hazeState = rememberHazeState()
    val reducedMotion = rememberReducedMotion()
    val errorColor = MaterialTheme.colorScheme.error

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Блокуємо дотики до екрана під накладкою.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .semantics { paneTitle = "Ви не в мережі" }
    ) {
        // Фон для «скла».
        Canvas(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
            drawCircle(
                Brush.radialGradient(listOf(errorColor.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width * 0.5f, size.height * 0.32f), radius = size.width * 0.8f),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.5f, size.height * 0.32f)
            )
            drawCircle(
                Brush.radialGradient(listOf(AccentPrimary.copy(alpha = 0.18f), Color.Transparent), center = Offset(size.width * 0.9f, size.height * 0.85f), radius = size.width * 0.6f),
                radius = size.width * 0.6f,
                center = Offset(size.width * 0.9f, size.height * 0.85f)
            )
        }

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
            PulsingWifiOff(color = errorColor, animate = !reducedMotion)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(top = 32.dp)
                    .fillMaxWidth()
                    .liquidGlass(hazeState, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                // TalkBack одразу оголошує стан (live region).
                Text(
                    text = "Ви не в мережі",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Assertive
                        contentDescription = "Ви не в мережі. Перевірте підключення до інтернету"
                    }
                )
                Text(
                    text = "Перевірте підключення до інтернету. Мапа й дані з'являться, щойно зв'язок відновиться.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (stillOffline) {
                    Text(
                        text = "Підключення ще немає. Спробуйте трохи згодом.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }

            PrimaryButton(
                text = "Спробувати ще раз",
                onClick = onRetry,
                loading = isChecking,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .semantics { if (isChecking) contentDescription = "Перевіряємо підключення" }
            )
        }
    }
}

/** Червоне коло ~120 dp з білою іконкою вимкненого Wi‑Fi і м'яким пульсуючим світінням. */
@Composable
private fun PulsingWifiOff(color: Color, animate: Boolean) {
    val glow = if (animate) {
        val transition = rememberInfiniteTransition(label = "offline-glow")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
            label = "offline-glow-progress"
        ).value
    } else {
        0.5f
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val base = 60.dp.toPx()
            val glowRadius = base + (18.dp.toPx() + 22.dp.toPx() * glow)
            drawCircle(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.45f + 0.2f * glow), Color.Transparent),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .background(color, CircleShape)
        ) {
            Icon(
                Icons.Filled.WifiOff,
                contentDescription = "Немає підключення до мережі",
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }
    }
}
