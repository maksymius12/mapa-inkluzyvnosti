package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.academy.mapainkluzyvnosti.ui.theme.ManropeFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SosCoral = Color(0xFFFF7B5C)
private val SosRed = Color(0xFFFF3D3D)
private val SosGlow = Color(0xFFFF4D4D)

/** Темне «гало» навколо білих літер: за WCAG 1.4.3 широке гало вважається тлом тексту → достатній контраст. */
private val TextHalo = Shadow(color = Color(0xCC5A0A0A), offset = Offset.Zero, blurRadius = 8f)

private const val RING_CYCLE_MS = 1100

/** Скільки тримати SOS, щоб надіслати: захист від випадкового натискання (кишеня, дитина, промах). */
const val SOS_HOLD_MS = 1000
private const val SOS_DESCRIPTION = "Кнопка SOS. Утримуйте одну секунду, щоб одразу надіслати запит допомоги волонтерам"

/**
 * Червоне градієнтне коло «SOS» зі світінням і кільцями, що розходяться (до [ringMaxScale]).
 * Якщо анімації вимкнено в системі, кільця статичні й напівпрозорі.
 *
 * Надсилає запит після утримання [SOS_HOLD_MS] — біле кільце по краю заповнюється; відпустили раніше —
 * [onShortTap] (підказка «Утримуйте»). Для TalkBack / Switch Access (подвійний дотик — уже свідоме
 * підтвердження) дія доступна як звичайне натискання через семантику.
 */
@Composable
private fun SosCircle(
    onHoldComplete: () -> Unit,
    onShortTap: () -> Unit,
    diameter: Dp,
    ringMaxScale: Float,
    glowRadius: Dp,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnHoldComplete by rememberUpdatedState(onHoldComplete)
    val currentOnShortTap by rememberUpdatedState(onShortTap)
    var pressed by remember { mutableStateOf(false) }
    val holdProgress = remember { Animatable(0f) }
    val pressScale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "sos-press")
    val diameterPx = with(LocalDensity.current) { diameter.toPx() }

    val ringPhase = if (reducedMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "sos-rings").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(RING_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "sos-ring-phase"
        ).value
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(diameter)
            // Світіння й кільця — поза межами кола, не стискаються при натисканні.
            .drawBehind {
                val radius = size.minDimension / 2f
                val glow = radius + glowRadius.toPx()
                drawCircle(
                    Brush.radialGradient(
                        0f to SosGlow.copy(alpha = 0.6f),
                        radius / glow to SosGlow.copy(alpha = 0.6f),
                        1f to Color.Transparent,
                        center = center,
                        radius = glow
                    ),
                    radius = glow
                )
                val stroke = Stroke(width = 2.dp.toPx())
                if (ringPhase == null) {
                    drawCircle(SosGlow.copy(alpha = 0.35f), radius = radius * (1f + (ringMaxScale - 1f) * 0.5f), style = stroke)
                } else {
                    for (phase in listOf(ringPhase, (ringPhase + 0.5f) % 1f)) {
                        val scale = 1f + (ringMaxScale - 1f) * phase
                        drawCircle(SosGlow.copy(alpha = 0.7f * (1f - phase)), radius = radius * scale, style = stroke)
                    }
                }
            }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(SosCoral, SosRed),
                    center = Offset(diameterPx * 0.40f, diameterPx * 0.35f),
                    radius = diameterPx * 0.68f
                )
            )
            // Заповнення під час утримання: біла дуга по краю кола.
            .drawWithContent {
                drawContent()
                val progress = holdProgress.value
                if (progress > 0f) {
                    val stroke = 4.dp.toPx()
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
            .semantics {
                contentDescription = SOS_DESCRIPTION
                role = Role.Button
                onClick(label = "надіслати запит допомоги") {
                    currentOnHoldComplete()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    val hold = scope.launch {
                        holdProgress.snapTo(0f)
                        holdProgress.animateTo(1f, tween(SOS_HOLD_MS, easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnHoldComplete()
                    }
                    val released = tryAwaitRelease()
                    pressed = false
                    if (hold.isActive) {
                        hold.cancel()
                        if (released) currentOnShortTap()
                        scope.launch { holdProgress.animateTo(0f, tween(150)) }
                    } else {
                        holdProgress.snapTo(0f)
                    }
                })
            }
    ) {
        Text(
            text = "SOS",
            modifier = Modifier.clearAndSetSemantics { },
            style = TextStyle(
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (diameter.value * 0.3f).sp,
                color = Color.White,
                shadow = TextHalo
            )
        )
    }
}

/**
 * SOS як окремий пункт нижньої навігації (по центру між вкладками): коло «SOS» і підпис «Допомога»,
 * як у сусідніх пунктів. Кільця невеликі, щоб уміститися в панелі. Коротке натискання лише змінює
 * підпис на «Утримуйте» — надсилає утримання.
 */
@Composable
fun RowScope.SosNavItem(onHoldComplete: () -> Unit) {
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(showHint) {
        if (showHint) {
            delay(2_500)
            showHint = false
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        // Без fillMaxHeight: рядок NavigationBar має лише мінімальну висоту (80 dp), і заповнення висоти
        // розтягнуло б усю панель на весь екран.
        modifier = Modifier
            .weight(1f)
            .align(Alignment.CenterVertically)
            .padding(vertical = 6.dp)
    ) {
        SosCircle(
            onHoldComplete = onHoldComplete,
            onShortTap = { showHint = true },
            diameter = 44.dp,
            ringMaxScale = 1.45f,
            glowRadius = 8.dp
        )
        Text(
            text = if (showHint) "Утримуйте" else "Допомога",
            style = MaterialTheme.typography.labelMedium,
            color = SosRed,
            modifier = Modifier
                .padding(top = 4.dp)
                .clearAndSetSemantics { }
        )
    }
}

/**
 * Більша SOS-кнопка для екранів без нижньої панелі (маршрут, екран SOS): коло + підпис під ним.
 * Надсилає після утримання [SOS_HOLD_MS]; коротке натискання показує «Утримуйте 1 секунду».
 */
@Composable
fun SosHelpButton(
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 64.dp,
    caption: String = "Це не заміна 112"
) {
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(showHint) {
        if (showHint) {
            delay(2_500)
            showHint = false
        }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SosCircle(
            onHoldComplete = onHoldComplete,
            onShortTap = { showHint = true },
            diameter = diameter,
            ringMaxScale = 1.8f,
            glowRadius = 14.dp
        )
        Text(
            text = if (showHint) "Утримуйте 1 секунду" else caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
