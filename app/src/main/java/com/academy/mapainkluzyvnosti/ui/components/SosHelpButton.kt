package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.academy.mapainkluzyvnosti.ui.theme.ManropeFontFamily

private val SosCoral = Color(0xFFFF7B5C)
private val SosRed = Color(0xFFFF3D3D)
private val SosGlow = Color(0xFFFF4D4D)

/** Темне «гало» навколо білих літер: за WCAG 1.4.3 широке гало вважається тлом тексту → достатній контраст. */
private val TextHalo = Shadow(color = Color(0xCC5A0A0A), offset = Offset.Zero, blurRadius = 8f)

private const val RING_CYCLE_MS = 1100
private const val SOS_DESCRIPTION = "Кнопка запиту допомоги. Відкриває запит до волонтерів"

/**
 * Червоне градієнтне коло «SOS» зі світінням і кільцями, що розходяться (до [ringMaxScale]).
 * Якщо анімації вимкнено в системі, кільця статичні й напівпрозорі.
 */
@Composable
private fun SosCircle(
    onClick: () -> Unit,
    diameter: Dp,
    ringMaxScale: Float,
    glowRadius: Dp,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
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
            .semantics { contentDescription = SOS_DESCRIPTION }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                role = Role.Button,
                onClickLabel = "відкрити запит допомоги",
                onClick = onClick
            )
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
 * як у сусідніх пунктів. Кільця невеликі, щоб уміститися в панелі.
 */
@Composable
fun RowScope.SosNavItem(onClick: () -> Unit) {
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
        SosCircle(onClick = onClick, diameter = 44.dp, ringMaxScale = 1.45f, glowRadius = 8.dp)
        Text(
            text = "Допомога",
            style = MaterialTheme.typography.labelMedium,
            color = SosRed,
            modifier = Modifier
                .padding(top = 4.dp)
                .clearAndSetSemantics { }
        )
    }
}

/** Компактна SOS-кнопка для екранів без нижньої панелі (маршрут): коло + «Це не заміна 112». */
@Composable
fun SosHelpButton(onClick: () -> Unit, modifier: Modifier = Modifier, diameter: Dp = 64.dp) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SosCircle(onClick = onClick, diameter = diameter, ringMaxScale = 1.8f, glowRadius = 14.dp)
        Text(
            text = "Це не заміна 112",
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
