package com.academy.mapainkluzyvnosti.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightBackground = Color(0xFFFFFFFF)
private val LightSurface = Color(0xFFF2F2F5)
private val LightTextPrimary = Background

private val MapaDarkColorScheme = darkColorScheme(
    primary = AccentPrimary,
    secondary = AccentSecondary,
    tertiary = AccentSecondary,
    background = Background,
    surface = Surface,
    surfaceVariant = Surface,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = StatusBarrier,
    onError = TextPrimary
)

private val MapaLightColorScheme = lightColorScheme(
    primary = AccentPrimary,
    secondary = AccentSecondary,
    tertiary = AccentSecondary,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurface,
    onPrimary = LightBackground,
    onSecondary = LightBackground,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    error = StatusBarrier,
    onError = LightBackground
)

val MapaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Мапа Інклюзивності — тема застосунку. Темна тема основна за задумом дизайну. */
@Composable
fun MapaInkluzyvnostiTheme(darkTheme: Boolean = true, largeText: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) MapaDarkColorScheme else MapaLightColorScheme,
        typography = if (largeText) MapaTypographyLarge else MapaTypography,
        shapes = MapaShapes,
        content = content
    )
}
