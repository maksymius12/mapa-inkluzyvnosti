package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Спільні компоненти дизайн-системи «Мапи Інклюзивності»:
// кнопки, чипи, картки, кольорові іконки, заголовок екрана, перемикач.
// Радіуси: картки 16-20 dp, кнопки 14-16 dp; тонкі світлі обвідки на поверхнях.
// ---------------------------------------------------------------------------

val CardRadius = 18.dp
val ButtonRadius = 16.dp
private val ButtonHeight = 54.dp

/** Тонка обвідка поверхні (картки, поля, плаваючі кнопки). */
@Composable
fun subtleBorder(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/** Основна кнопка: яскраво-синя, білий жирний текст, на всю ширину. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(ButtonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight)
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Box(Modifier.size(8.dp))
            }
            Text(text = text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** Вторинна кнопка: контурна, на всю ширину. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(ButtonRadius),
        border = subtleBorder(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text = text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/** Чип-фільтр: активний — синій із білим текстом, неактивний — поверхня з тонкою обвідкою. */
@Composable
fun MapaChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Картка-поверхня із закругленими кутами й тонкою світлою обвідкою. */
@Composable
fun MapaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(CardRadius),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        content = content
    )
}

/**
 * Заповнена іконка всередині кольорового кола (або заокругленого квадрата). Якщо [glyph]
 * задано (літера «P» паркування), малюється він замість значка.
 */
@Composable
fun IconBadge(
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    glyph: String? = null,
    size: Dp = 40.dp,
    shape: Shape = CircleShape,
    tint: Color = Color.White,
    solid: Boolean = true
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (solid) color else color.copy(alpha = 0.18f))
    ) {
        val contentColor = if (solid) tint else color
        when {
            icon != null -> Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(size * 0.56f))
            glyph != null -> Text(text = glyph, color = contentColor, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.5f).sp)
        }
    }
}

/** Кольорова іконка типу доступності за легендою (коло). */
@Composable
fun AccessibilityTypeBadge(
    type: com.academy.mapainkluzyvnosti.data.model.AccessibilityType,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = CircleShape,
    muted: Boolean = false
) {
    val visual = type.visual
    IconBadge(
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) else visual.color,
        icon = visual.icon,
        glyph = visual.glyph,
        size = size,
        shape = shape,
        tint = if (muted) Color.White else visual.glyphColor,
        modifier = modifier
    )
}

/** Заголовок екрана: стрілка назад зліва, жирна назва, опційно хрестик і дії справа. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .heightIn(min = 48.dp)
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
        } else {
            Box(Modifier.size(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Start,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        actions()
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Закрити")
            }
        }
    }
}

/** Перемикач у стилі макета: активний — синій трек і білий бігунок. */
@Composable
fun MapaSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

/** Підпис секції (дрібний сірий жирний). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

/** Рядок вирівняний по центру з рівними проміжками — заготовка для рядків списків. */
@Composable
fun SpacedRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}
