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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.ui.components.rememberReducedMotion
import com.academy.mapainkluzyvnosti.ui.components.visual
import com.academy.mapainkluzyvnosti.ui.theme.AccentPrimary
import com.academy.mapainkluzyvnosti.ui.theme.CriterionNo
import com.academy.mapainkluzyvnosti.ui.theme.CriterionYes
import com.academy.mapainkluzyvnosti.ui.theme.OnCriterionChoice
import com.academy.mapainkluzyvnosti.ui.theme.StatusAccessible
import com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier
import com.academy.mapainkluzyvnosti.ui.theme.StatusPartial

/**
 * Векторна ілюстрація слайда (Canvas + іконки, без растрових картинок) з легкою появою
 * (з «Вимкнути анімацію» — одразу видно). Для TalkBack уся ілюстрація — один елемент зі смисловим описом.
 */
@Composable
fun TutorialIllustrationView(slide: TutorialSlide, isCurrent: Boolean, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(isCurrent) {
        if (isCurrent && !reducedMotion) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
        }
    }
    val p = progress.value
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            // Мінімум 200dp, щоб слайди не стрибали; картка перевірки з великим шрифтом може бути вищою.
            .heightIn(min = 200.dp)
            .clearAndSetSemantics { contentDescription = slide.illustrationDescription }
    ) {
        when (slide.illustration) {
            TutorialIllustration.MAP_MARKERS -> MapMarkersArt(p)
            TutorialIllustration.FIND -> FindArt(p)
            TutorialIllustration.CHECK_CARD -> CheckCardArt(p)
            TutorialIllustration.ROLES -> RolesArt(p)
            TutorialIllustration.SIGN_IN -> SignInArt(p)
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

/** Як на мапі (PlaceMarker): коло з іконкою категорії, у правому нижньому куті — значок статусу. */
@Composable
private fun CategoryMarker(category: PlaceCategory, status: Color?, size: Dp = 52.dp) {
    val visual = category.visual
    Box(modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val c = Offset(s * 0.44f, s * 0.44f)
            val r = s * 0.42f
            drawCircle(Color.White, radius = r, center = c)
            drawCircle(visual.color, radius = r - s * 0.055f, center = c)

            val br = s * 0.17f
            val bc = Offset(s - br - s * 0.03f, s - br - s * 0.03f)
            val ring = s * 0.04f
            if (status == null) {
                // «Не перевірено» — пунктирне кільце на темному тлі.
                drawCircle(Color(0xFF1B2333), radius = br, center = bc)
                drawCircle(
                    Color.White,
                    radius = br - ring / 2f,
                    center = bc,
                    style = Stroke(width = ring, pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.045f, s * 0.035f)))
                )
            } else {
                drawCircle(Color.White, radius = br, center = bc)
                drawCircle(status, radius = br - ring, center = bc)
            }
        }
        Icon(
            visual.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.padding(start = size * 0.19f, top = size * 0.19f).size(size * 0.5f)
        )
    }
}

@Composable
private fun MapCanvas(p: Float, content: @Composable () -> Unit) {
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
        content()
    }
}

@Composable
private fun MapMarkersArt(p: Float) {
    MapCanvas(p) {
        Box(Modifier.padding(start = 22.dp, top = 18.dp).appear(p, 0.15f)) { CategoryMarker(PlaceCategory.EDUCATION, StatusAccessible) }
        Box(Modifier.padding(start = 150.dp, top = 12.dp).appear(p, 0.3f)) { CategoryMarker(PlaceCategory.HEALTH, StatusPartial) }
        Box(Modifier.padding(start = 86.dp, top = 96.dp).appear(p, 0.45f)) { CategoryMarker(PlaceCategory.CAFE, StatusBarrier) }
        Box(Modifier.padding(start = 190.dp, top = 104.dp).appear(p, 0.6f)) { CategoryMarker(PlaceCategory.SHOP, null) }
    }
}

@Composable
private fun FindArt(p: Float) {
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
        }
        Box(modifier = Modifier.size(width = 260.dp, height = 110.dp).appear(p, 0.2f).background(Color(0xFF1B2640), RoundedCornerShape(20.dp))) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(size.width * 0.1f, size.height * 0.8f)
                    cubicTo(size.width * 0.3f, size.height * 0.8f, size.width * 0.35f, size.height * 0.3f, size.width * 0.55f, size.height * 0.4f)
                    cubicTo(size.width * 0.7f, size.height * 0.5f, size.width * 0.72f, size.height * 0.3f, size.width * 0.85f, size.height * 0.3f)
                }
                drawPath(path, AccentPrimary, style = Stroke(width = 9f * p.coerceAtLeast(0.01f), cap = StrokeCap.Round))
                drawCircle(Color.White, radius = 9f, center = Offset(size.width * 0.1f, size.height * 0.8f))
            }
            Box(Modifier.padding(start = 118.dp, top = 22.dp).appear(p, 0.4f)) { IconBubble(Icons.Filled.Warning, StatusBarrier, 32.dp) }
            Box(Modifier.padding(start = 200.dp, top = 8.dp).appear(p, 0.55f)) { IconBubble(Icons.Filled.Directions, AccentPrimary, 40.dp) }
            Text(
                "Відчинено зараз",
                color = OnCriterionChoice,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .appear(p, 0.7f)
                    .background(StatusAccessible, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun CheckCardArt(p: Float) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .appear(p)
            .width(260.dp)
            .background(Color(0xFF141C2E), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        GroupCaption("КРИТИЧНІ")
        CriterionLine(yes = true, progress = p, delay = 0.2f)
        CriterionLine(yes = null, progress = p, delay = 0.35f)
        GroupCaption("ДОДАТКОВІ")
        CriterionLine(yes = false, progress = p, delay = 0.5f)
    }
}

@Composable
private fun GroupCaption(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp)
}

/** Рядок критерію: «текст» + перемикач «Так | Ні»; [yes] = null — ще нічого не вибрано. */
@Composable
private fun CriterionLine(yes: Boolean?, progress: Float, delay: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().appear(progress, delay)) {
        Box(Modifier.weight(1f).height(10.dp).padding(end = 24.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(5.dp)))
        Row(
            modifier = Modifier
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(1.dp)
        ) {
            ToggleChip("Так", if (yes == true) CriterionYes else null)
            ToggleChip("Ні", if (yes == false) CriterionNo else null)
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Color?) {
    Text(
        label,
        color = if (selected != null) OnCriterionChoice else Color.White.copy(alpha = 0.8f),
        fontWeight = if (selected != null) FontWeight.Bold else FontWeight.Medium,
        fontSize = 12.sp,
        modifier = Modifier
            .background(selected ?: Color.Transparent, RoundedCornerShape(7.dp))
            .padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

@Composable
private fun RolesArt(p: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconBubble(Icons.Filled.Person, AccentPrimary, 72.dp, Modifier.appear(p))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(32.dp).appear(p, 0.3f)
        )
        IconBubble(Icons.Filled.Groups, StatusAccessible, 72.dp, Modifier.appear(p, 0.5f))
    }
}

@Composable
private fun SignInArt(p: Float) {
    IconBubble(Icons.AutoMirrored.Filled.Login, AccentPrimary, 96.dp, Modifier.appear(p))
}

@Composable
private fun IconBubble(icon: ImageVector, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
            .border(if (size < 48.dp) 2.dp else 3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}
