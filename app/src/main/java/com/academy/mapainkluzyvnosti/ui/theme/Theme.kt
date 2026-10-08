package com.academy.mapainkluzyvnosti.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MapaDarkColorScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = Color.White,
    primaryContainer = AccentPrimary.copy(alpha = 0.22f),
    onPrimaryContainer = TextPrimary,
    secondary = AccentSecondary,
    onSecondary = Color.White,
    tertiary = AccentSecondary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Background,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHigh,
    outline = OutlineSubtle,
    outlineVariant = OutlineSubtle,
    error = StatusBarrier,
    onError = Color.White
)

private val MapaLightColorScheme = lightColorScheme(
    primary = AccentPrimary,
    onPrimary = Color.White,
    primaryContainer = AccentPrimary.copy(alpha = 0.14f),
    onPrimaryContainer = LightTextPrimary,
    secondary = AccentSecondary,
    onSecondary = Color.White,
    tertiary = AccentSecondary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHigh,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = StatusBarrier,
    onError = Color.White
)

val MapaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Мапа Інклюзивності — тема застосунку. Темна тема основна за макетом. */
@Composable
fun MapaInkluzyvnostiTheme(darkTheme: Boolean = true, largeText: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) MapaDarkColorScheme else MapaLightColorScheme,
        typography = if (largeText) MapaTypographyLarge else MapaTypography,
        shapes = MapaShapes,
        content = content
    )
}
