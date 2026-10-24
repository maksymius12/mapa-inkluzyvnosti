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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.academy.mapainkluzyvnosti.ui.theme.ManropeFontFamily

private val SosCoral = Color(0xFFFF7B5C)
private val SosRed = Color(0xFFFF3D3D)
private val SosGlow = Color(0xFFFF4D4D)

/** Темне «гало» навколо білих літер: за WCAG 1.4.3 широке гало вважається тлом тексту → достатній контраст. */
private val TextHalo = Shadow(color = Color(0xCC5A0A0A), offset = Offset.Zero, blurRadius = 10f)

private const val RING_CYCLE_MS = 1100
private const val RING_MAX_SCALE = 2.5f

/**
 * Кнопка запиту допомоги в стилі промо-відео: червоний градієнтний круг із «SOS» і «Потрібна допомога»,
 * м'яке світіння й два кільця, що розходяться. Лише вигляд — дія та сама ([onClick]).
 * Навмисно без вібрації: в застосунку вібровідгуку ніде немає.
 */
@Composable
fun SosHelpButton(onClick: () -> Unit, modifier: Modifier = Modifier, diameter: Dp = 150.dp) {
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

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(diameter)
                // Світіння й кільця малюються поза межами кола й не стискаються при натисканні.
                .drawBehind {
                    val radius = size.minDimension / 2f
                    val glowRadius = radius + 40.dp.toPx()
                    drawCircle(
                        Brush.radialGradient(
                            0f to SosGlow.copy(alpha = 0.65f),
                            radius / glowRadius to SosGlow.copy(alpha = 0.65f),
                            1f to Color.Transparent,
                            center = center,
                            radius = glowRadius
                        ),
                        radius = glowRadius
                    )
                    val stroke = Stroke(width = 3.dp.toPx())
                    if (ringPhase == null) {
                        // Анімації вимкнено в системі: кільця статичні й напівпрозорі.
                        drawCircle(SosGlow.copy(alpha = 0.35f), radius = radius * 1.35f, style = stroke)
                        drawCircle(SosGlow.copy(alpha = 0.35f), radius = radius * 1.75f, style = stroke)
                    } else {
                        for (phase in listOf(ringPhase, (ringPhase + 0.5f) % 1f)) {
                            val scale = 1f + (RING_MAX_SCALE - 1f) * phase
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
                        // Світліший центр зміщено вгору-вліво (40% / 35%), насичений червоний — по краю.
                        center = Offset(diameterPx * 0.40f, diameterPx * 0.35f),
                        radius = diameterPx * 0.68f
                    )
                )
                // Ледь помітне затемнення під текстом — разом із гало дає контраст ≥ 4,5:1.
                .background(
                    Brush.radialGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.6f to Color.Black.copy(alpha = 0.12f),
                        1f to Color.Transparent
                    )
                )
                .semantics { contentDescription = "Кнопка запиту допомоги. Надсилає сигнал волонтерам" }
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White),
                    role = Role.Button,
                    onClickLabel = "відкрити запит допомоги",
                    onClick = onClick
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .clearAndSetSemantics { }
            ) {
                Text(
                    text = "SOS",
                    style = TextStyle(
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 36.sp,
                        color = Color.White,
                        shadow = TextHalo
                    )
                )
                Text(
                    text = "Потрібна допомога",
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        shadow = TextHalo
                    )
                )
            }
        }
        Text(
            text = "Це не заміна 112",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
