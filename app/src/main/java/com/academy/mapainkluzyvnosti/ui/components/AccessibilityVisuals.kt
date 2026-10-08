package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Elevator
import androidx.compose.material.icons.filled.Hail
import androidx.compose.material.icons.filled.Wc
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.academy.mapainkluzyvnosti.data.model.AccessibilityType
import com.academy.mapainkluzyvnosti.ui.theme.LegendElevator
import com.academy.mapainkluzyvnosti.ui.theme.LegendParking
import com.academy.mapainkluzyvnosti.ui.theme.LegendRamp
import com.academy.mapainkluzyvnosti.ui.theme.LegendStaffHelp
import com.academy.mapainkluzyvnosti.ui.theme.LegendToilet

/**
 * Колірна легенда мапи: пандус — синій, туалет — зелений, паркування — жовтий (літера P),
 * ліфт — фіолетовий, допомога персоналу — рожевий. [icon] = null означає, що замість значка
 * малюється [glyph] (літера «P»).
 */
data class AccessibilityVisual(
    val color: Color,
    val icon: ImageVector?,
    val glyph: String?,
    val glyphColor: Color,
    /** Назва у множині — для фільтрів і чипів. */
    val label: String,
    /** Назва в однині — для плиток деталей. */
    val singularLabel: String
)

private val DarkGlyph = Color(0xFF1A1F2B)

val AccessibilityType.visual: AccessibilityVisual
    get() = when (this) {
        AccessibilityType.RAMP -> AccessibilityVisual(LegendRamp, Icons.Filled.Accessible, null, Color.White, "Пандуси", "Пандус")
        AccessibilityType.TOILET -> AccessibilityVisual(LegendToilet, Icons.Filled.Wc, null, Color.White, "Туалети", "Туалет")
        AccessibilityType.PARKING -> AccessibilityVisual(LegendParking, null, "P", DarkGlyph, "Паркування", "Паркування")
        AccessibilityType.ELEVATOR -> AccessibilityVisual(LegendElevator, Icons.Filled.Elevator, null, Color.White, "Ліфти", "Ліфт")
        AccessibilityType.STAFF_HELP -> AccessibilityVisual(LegendStaffHelp, Icons.Filled.Hail, null, Color.White, "Допомога персоналу", "Допомога персоналу")
    }
