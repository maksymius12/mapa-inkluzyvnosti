package com.academy.mapainkluzyvnosti.ui.screens.tutorial

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Elevator
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.ui.theme.AccentPrimary
import com.academy.mapainkluzyvnosti.ui.theme.LegendElevator
import com.academy.mapainkluzyvnosti.ui.theme.LegendNeutral
import com.academy.mapainkluzyvnosti.ui.theme.LegendParking
import com.academy.mapainkluzyvnosti.ui.theme.LegendRamp
import com.academy.mapainkluzyvnosti.ui.theme.LegendStaffHelp
import com.academy.mapainkluzyvnosti.ui.theme.LegendToilet
import com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier
import com.academy.mapainkluzyvnosti.ui.theme.StatusPartial

/**
 * Векторна ілюстрація слайда (Canvas + іконки, без растрових картинок) з легкою появою.
 * Для TalkBack уся ілюстрація — один елемент зі смисловим описом.
 */
@Composable
fun TutorialIllustrationView(slide: TutorialSlide, isCurrent: Boolean, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
        }
    }
    val p = progress.value
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clearAndSetSemantics { contentDescription = slide.illustrationDescription }
    ) {
        when (slide.illustration) {
            TutorialIllustration.MAP_MARKERS -> MapMarkersArt(p)
            TutorialIllustration.SEARCH -> SearchArt(p)
            TutorialIllustration.ROUTE -> RouteArt(p)
            TutorialIllustration.CONTRIBUTE -> ContributeArt(p)
            TutorialIllustration.SIGN_IN -> SignInArt(p)
            TutorialIllustration.HELP_REQUEST -> HelpRequestArt(p)
        }
    }
}

/** Поява елемента з затримкою [delay] (частка загального прогресу): масштаб і прозорість. */
private fun Modifier.appear(progress: Float, delay: Float = 0f): Modifier = graphicsLayer {
    val local = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
    alpha = local
    scaleX = 0.7f + 0.3f * local
    scaleY = 0.7f + 0.3f * local
}

@Composable
private fun Marker(color: Color, icon: ImageVector?, size: Dp = 44.dp, dashed: Boolean = false, glyph: String? = null) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            if (dashed) {
                drawCircle(color, radius = r * 0.86f)
                drawCircle(
                    Color.White,
                    radius = r * 0.93f,
                    style = Stroke(width = r * 0.14f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.3f, r * 0.22f)), cap = StrokeCap.Round)
                )
            } else {
                drawCircle(Color.White, radius = r)
                drawCircle(color, radius = r * 0.86f)
            }
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
        } else if (glyph != null) {
            Text(glyph, color = Color(0xFF1A1F2B), fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun MapMarkersArt(p: Float) {
    Box(
        modifier = Modifier
            .size(width = 260.dp, height = 180.dp)
            .appear(p)
            .background(Color(0xFF1B2640), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val road = Color.White.copy(alpha = 0.12f)
            drawLine(road, Offset(0f, size.height * 0.35f), Offset(size.width, size.height * 0.55f), strokeWidth = 14f)
            drawLine(road, Offset(size.width * 0.3f, 0f), Offset(size.width * 0.45f, size.height), strokeWidth = 12f)
            drawLine(road, Offset(size.width * 0.75f, 0f), Offset(size.width * 0.65f, size.height), strokeWidth = 10f)
        }
        Box(Modifier.padding(start = 24.dp, top = 22.dp).appear(p, 0.15f)) { Marker(LegendRamp, Icons.AutoMirrored.Filled.Accessible) }
        Box(Modifier.padding(start = 150.dp, top = 16.dp).appear(p, 0.25f)) { Marker(LegendToilet, Icons.Filled.Wc) }
        Box(Modifier.padding(start = 92.dp, top = 92.dp).appear(p, 0.35f)) { Marker(LegendParking, null, glyph = "P") }
        Box(Modifier.padding(start = 196.dp, top = 102.dp).appear(p, 0.45f)) { Marker(LegendElevator, Icons.Filled.Elevator) }
        Box(Modifier.padding(start = 20.dp, top = 112.dp).appear(p, 0.55f)) { Marker(LegendStaffHelp, Icons.Filled.Groups, size = 40.dp) }
        Box(Modifier.padding(start = 206.dp, top = 30.dp).appear(p, 0.65f)) { Marker(LegendNeutral, null, size = 34.dp, dashed = true) }
    }
}

@Composable
private fun SearchArt(p: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .appear(p)
                .width(260.dp)
                .height(52.dp)
                .background(Color(0xFF1B2640), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
            Box(Modifier.padding(start = 10.dp).width(140.dp).height(10.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(5.dp)))
            Box(Modifier.weight(1f))
            Icon(Icons.Filled.Tune, contentDescription = null, tint = AccentPrimary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AccentPrimary to 64.dp, LegendToilet to 78.dp, LegendElevator to 56.dp).forEachIndexed { i, (color, width) ->
                Box(
                    Modifier
                        .appear(p, 0.2f + i * 0.15f)
                        .width(width)
                        .height(32.dp)
                        .background(color.copy(alpha = if (i == 0) 1f else 0.3f), RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
private fun RouteArt(p: Float) {
    Box(
        modifier = Modifier
            .size(width = 260.dp, height = 180.dp)
            .appear(p)
            .background(Color(0xFF1B2640), RoundedCornerShape(24.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(size.width * 0.12f, size.height * 0.8f)
                cubicTo(size.width * 0.35f, size.height * 0.8f, size.width * 0.3f, size.height * 0.3f, size.width * 0.55f, size.height * 0.35f)
                cubicTo(size.width * 0.75f, size.height * 0.4f, size.width * 0.7f, size.height * 0.2f, size.width * 0.86f, size.height * 0.2f)
            }
            drawPath(path, AccentPrimary, style = Stroke(width = 10f * p.coerceAtLeast(0.01f), cap = StrokeCap.Round))
            drawCircle(Color.White, radius = 10f, center = Offset(size.width * 0.12f, size.height * 0.8f))
        }
        Box(Modifier.padding(start = 122.dp, top = 40.dp).appear(p, 0.4f)) { Marker(StatusBarrier, Icons.Filled.Warning, size = 38.dp) }
        Box(Modifier.padding(start = 200.dp, top = 8.dp).appear(p, 0.6f)) { Marker(AccentPrimary, Icons.Filled.Directions, size = 46.dp) }
    }
}

@Composable
private fun ContributeArt(p: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        IconBubble(Icons.Filled.Add, AccentPrimary, 88.dp, Modifier.appear(p))
        IconBubble(Icons.AutoMirrored.Filled.PlaylistAddCheck, Color(0xFF2E9E6B), 64.dp, Modifier.appear(p, 0.3f))
        IconBubble(Icons.Filled.CameraAlt, Color(0xFF7B5FE0), 64.dp, Modifier.appear(p, 0.55f))
    }
}

@Composable
private fun SignInArt(p: Float) {
    IconBubble(Icons.AutoMirrored.Filled.Login, AccentPrimary, 96.dp, Modifier.appear(p))
}

@Composable
private fun HelpRequestArt(p: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp).appear(p)) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = size.minDimension * 0.08f
                val triangle = Path().apply {
                    moveTo(size.width / 2f, inset)
                    lineTo(size.width - inset, size.height - inset)
                    lineTo(inset, size.height - inset)
                    close()
                }
                drawPath(triangle, StatusPartial)
                drawPath(triangle, Color.White, style = Stroke(width = size.minDimension * 0.045f))
            }
            Icon(Icons.Filled.PriorityHigh, contentDescription = null, tint = Color.White, modifier = Modifier.padding(top = 22.dp).size(40.dp))
        }
        IconBubble(Icons.Filled.Groups, Color(0xFF2E9E6B), 72.dp, Modifier.appear(p, 0.4f))
    }
}

@Composable
private fun IconBubble(icon: ImageVector, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
            .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}
