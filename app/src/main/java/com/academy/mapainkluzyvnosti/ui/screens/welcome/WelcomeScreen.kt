package com.academy.mapainkluzyvnosti.ui.screens.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/** Екран 1: запуск. Логотип, назва, підпис району, силует Києва, кнопка «Почати». */
@Composable
fun WelcomeScreen(onStart: () -> Unit, onSignIn: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Spacer(Modifier.weight(1f))
        AppLogo(width = 112.dp)
        Text(
            text = "Мапа\nІнклюзивності",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = "Шевченківський район",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.weight(1f))

        KyivSkyline(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp)
        ) {
            PrimaryButton(text = "Почати", onClick = onStart)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 18.dp)
            ) {
                Text(
                    text = "Вже є акаунт? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Увійти",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onSignIn)
                )
            }
        }
    }
}

/** Логотип: пін із кільцевим градієнтом і символом візка всередині. */
@Composable
fun AppLogo(width: Dp, modifier: Modifier = Modifier) {
    val height = width * 1.14f
    val fill = MaterialTheme.colorScheme.background
    Box(contentAlignment = Alignment.TopCenter, modifier = modifier.size(width, height)) {
        Canvas(modifier = Modifier.size(width, height)) {
            val w = size.width
            val ringWidth = w * 0.085f
            val inset = ringWidth / 2f
            val r = w / 2f - inset
            val cx = w / 2f
            val cy = w / 2f
            val tipY = size.height - inset
            val d = tipY - cy
            val phi = acos((r / d).coerceIn(0f, 1f))
            val phiDeg = Math.toDegrees(phi.toDouble()).toFloat()
            val startDeg = 90f - phiDeg
            val sweepDeg = -(360f - 2f * phiDeg)
            val startRad = Math.toRadians(startDeg.toDouble())

            val path = Path().apply {
                moveTo(cx, tipY)
                lineTo(cx + r * cos(startRad).toFloat(), cy + r * sin(startRad).toFloat())
                arcTo(Rect(cx - r, cy - r, cx + r, cy + r), startDeg, sweepDeg, false)
                close()
            }
            drawPath(path, color = fill)
            drawPath(
                path,
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF5B7CFF),
                        Color(0xFF8B5CF6),
                        Color(0xFF14B8A6),
                        Color(0xFF4ADE80),
                        Color(0xFFFBBF24),
                        Color(0xFFFB923C),
                        Color(0xFF5B7CFF)
                    ),
                    center = Offset(cx, cy)
                ),
                style = Stroke(width = ringWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        Icon(
            imageVector = Icons.Filled.Accessible,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .padding(top = width * 0.5f - width * 0.27f)
                .size(width * 0.54f)
        )
    }
}

/** Контурна ілюстрація силуету Києва: храм із банями, дзвіниця, університет із колонадою, дерева. */
@Composable
fun KyivSkyline(modifier: Modifier = Modifier) {
    val stroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    Canvas(modifier = modifier.aspectRatio(3f)) {
        val u = size.width / 360f
        val sw = 1.3.dp.toPx()
        val style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(stroke, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), sw, StrokeCap.Round)

        fun rect(x: Float, y: Float, w: Float, h: Float) =
            drawRect(stroke, Offset(x * u, y * u), Size(w * u, h * u), style = style)

        fun dome(x: Float, y: Float, w: Float, h: Float) =
            drawArc(stroke, 180f, 180f, false, Offset(x * u, y * u), Size(w * u, h * 2 * u), style = style)

        fun windows(x: Float, y: Float, cols: Int, rows: Int, dx: Float, dy: Float, w: Float, h: Float) {
            for (c in 0 until cols) for (r in 0 until rows) rect(x + c * dx, y + r * dy, w, h)
        }

        fun tree(cx: Float, cy: Float, r: Float) {
            drawCircle(stroke, r * u, Offset(cx * u, cy * u), style = style)
            line(cx, cy + r, cx, 118f)
        }

        val ground = 118f
        line(0f, ground, 360f, ground)

        // Житлові будинки зліва
        rect(8f, 72f, 56f, ground - 72f)
        windows(14f, 80f, 4, 4, 13f, 9f, 6f, 5f)
        rect(64f, 86f, 40f, ground - 86f)
        windows(70f, 92f, 3, 3, 12f, 9f, 6f, 5f)

        // Храм із банями
        rect(122f, 82f, 56f, ground - 82f)
        rect(137f, 62f, 26f, 20f)
        dome(137f, 44f, 26f, 18f)
        rect(147f, 36f, 6f, 8f)
        line(150f, 36f, 150f, 26f)
        line(146f, 30f, 154f, 30f)
        dome(124f, 72f, 12f, 8f)
        dome(164f, 72f, 12f, 8f)
        windows(131f, 90f, 4, 1, 12f, 0f, 5f, 12f)

        // Дзвіниця
        rect(196f, 46f, 26f, ground - 46f)
        rect(201f, 26f, 16f, 20f)
        dome(201f, 12f, 16f, 14f)
        line(209f, 12f, 209f, 2f)
        line(206f, 6f, 212f, 6f)
        rect(204f, 54f, 10f, 14f)
        rect(204f, 74f, 10f, 14f)
        rect(204f, 94f, 10f, 14f)

        // Університет із портиком і колонадою
        rect(240f, 74f, 112f, ground - 74f)
        line(252f, 74f, 296f, 56f)
        line(296f, 56f, 340f, 74f)
        line(252f, 74f, 340f, 74f)
        for (i in 0 until 8) line(256f + i * 11f, 78f, 256f + i * 11f, ground)
        windows(246f, 84f, 1, 3, 0f, 10f, 5f, 6f)
        windows(342f, 84f, 1, 3, 0f, 10f, 5f, 6f)

        // Дерева
        tree(112f, 100f, 11f)
        tree(186f, 106f, 9f)
        tree(232f, 104f, 10f)
        tree(356f, 106f, 9f)
    }
}
