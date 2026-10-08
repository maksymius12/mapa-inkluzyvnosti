package com.academy.mapainkluzyvnosti.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.academy.mapainkluzyvnosti.R

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private fun googleFont(name: String): FontFamily {
    val font = GoogleFont(name)
    return FontFamily(
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Normal),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Medium),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Bold),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.ExtraBold)
    )
}

val ManropeFontFamily: FontFamily = googleFont("Manrope")

private const val LARGE_TEXT_SCALE = 1.15f

fun Typography.scaled(factor: Float): Typography = Typography(
    displayLarge = displayLarge.scale(factor),
    displayMedium = displayMedium.scale(factor),
    displaySmall = displaySmall.scale(factor),
    headlineLarge = headlineLarge.scale(factor),
    headlineMedium = headlineMedium.scale(factor),
    headlineSmall = headlineSmall.scale(factor),
    titleLarge = titleLarge.scale(factor),
    titleMedium = titleMedium.scale(factor),
    titleSmall = titleSmall.scale(factor),
    bodyLarge = bodyLarge.scale(factor),
    bodyMedium = bodyMedium.scale(factor),
    bodySmall = bodySmall.scale(factor),
    labelLarge = labelLarge.scale(factor),
    labelMedium = labelMedium.scale(factor),
    labelSmall = labelSmall.scale(factor)
)

private fun TextStyle.scale(factor: Float): TextStyle = copy(
    fontSize = fontSize * factor,
    lineHeight = lineHeight * factor
)

/** Typography для перемикача "Збільшений текст" у налаштуваннях доступності застосунку. */
val MapaTypographyLarge: Typography get() = MapaTypography.scaled(LARGE_TEXT_SCALE)

val MapaTypography = Typography(
    displayLarge = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 42.sp),
    displayMedium = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineLarge = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)
