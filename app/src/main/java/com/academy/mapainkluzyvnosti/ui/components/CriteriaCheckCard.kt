package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.academy.mapainkluzyvnosti.data.model.CheckResult
import com.academy.mapainkluzyvnosti.ui.theme.CriterionNo
import com.academy.mapainkluzyvnosti.ui.theme.CriterionYes
import com.academy.mapainkluzyvnosti.ui.theme.OnCriterionChoice

/**
 * 7 критеріїв доступності входу — єдине джерело назв і підказок для швидкої перевірки й ШІ-аналізу фото.
 * [key] — колонка checks / ключ у відповіді analyze-place-photo. Критичні (пандус, двері, поріг)
 * визначають «Недоступно» у ComputeAccessStatus.
 */
enum class AccessCriterion(val key: String, val title: String, val hint: String, val critical: Boolean) {
    RAMP("ramp", "Пандус", "Або вхід на рівні тротуару", critical = true),
    DOOR_WIDTH("door_width", "Ширина дверей", "Не менше 0,8 м", critical = true),
    THRESHOLD("threshold", "Поріг", "Не вище 2 см", critical = true),
    ELEVATOR("elevator", "Ліфт або перший поверх", "Без сходів усередині", critical = false),
    TOILET("toilet", "Доступний туалет", "Кабіна з місцем для візка", critical = false),
    TACTILE("tactile", "Тактильні позначки", "Для людей з порушенням зору", critical = false),
    STAFF("staff_assistance", "Допомога персоналу", "Працівники допоможуть зайти", critical = false);

    fun of(check: CheckResult): Boolean = when (this) {
        RAMP -> check.ramp
        DOOR_WIDTH -> check.doorWidth
        THRESHOLD -> check.threshold
        ELEVATOR -> check.elevator
        TOILET -> check.toilet
        TACTILE -> check.tactile
        STAFF -> check.staffAssistance
    }
}

/**
 * Форма швидкої перевірки. null — критерій ще не оцінено (обидві половинки перемикача нейтральні):
 * «Ні» за замовчуванням не ставимо. Паркування — додатковий атрибут поза 7 критеріями.
 */
data class QuickCheckState(
    val values: Map<AccessCriterion, Boolean?> = emptyMap(),
    val accessibleParking: Boolean? = null,
    val comment: String = ""
) {
    fun value(criterion: AccessCriterion): Boolean? = values[criterion]

    fun with(criterion: AccessCriterion, value: Boolean) = copy(values = values + (criterion to value))

    val unanswered: List<AccessCriterion> get() = AccessCriterion.entries.filter { values[it] == null }

    /** null, доки не оцінено всі 7 критеріїв. Паркування без відповіді — «ні», як і раніше. */
    fun toCheckResult(): CheckResult? {
        if (unanswered.isNotEmpty()) return null
        fun v(c: AccessCriterion) = values.getValue(c)!!
        return CheckResult(
            ramp = v(AccessCriterion.RAMP),
            doorWidth = v(AccessCriterion.DOOR_WIDTH),
            threshold = v(AccessCriterion.THRESHOLD),
            elevator = v(AccessCriterion.ELEVATOR),
            toilet = v(AccessCriterion.TOILET),
            tactile = v(AccessCriterion.TACTILE),
            staffAssistance = v(AccessCriterion.STAFF),
            accessibleParking = accessibleParking == true
        )
    }

    companion object {
        /** Останні збережені значення місця — щоб повторна перевірка починалась з них. */
        fun from(check: CheckResult?): QuickCheckState =
            if (check == null) QuickCheckState()
            else QuickCheckState(
                values = AccessCriterion.entries.associateWith { it.of(check) },
                accessibleParking = check.accessibleParking
            )
    }
}

/** Підпис під критерієм (наприклад, пропозиція ШІ з причиною). */
data class CriterionNote(val text: String, val fromAi: Boolean = false)

/**
 * Картка «Оцініть вхід до закладу»: групи «Критичні» і «Додаткові», у кожному рядку — назва, підказка
 * й перемикач «Так | Ні». [onValueChange] = null — лише перегляд (значення, запропоновані ШІ).
 */
@Composable
fun CriteriaCheckCard(
    values: (AccessCriterion) -> Boolean?,
    onValueChange: ((AccessCriterion, Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = "Оцініть вхід до закладу",
    notes: Map<AccessCriterion, CriterionNote> = emptyMap()
) {
    MapaCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            listOf(true, false).forEach { critical ->
                GroupLabel(
                    text = if (critical) "КРИТИЧНІ" else "ДОДАТКОВІ",
                    color = if (critical) CriterionNo else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = if (critical) 16.dp else 20.dp, bottom = 4.dp)
                )
                val group = AccessCriterion.entries.filter { it.critical == critical }
                group.forEachIndexed { index, criterion ->
                    CriterionRow(
                        title = criterion.title,
                        hint = criterion.hint,
                        value = values(criterion),
                        onValueChange = onValueChange?.let { change -> { v: Boolean -> change(criterion, v) } },
                        note = notes[criterion]
                    )
                    if (index < group.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** Форма швидкої перевірки: картка критеріїв, паркування (на статус не впливає) і коментар. */
@Composable
fun QuickCheckForm(
    state: QuickCheckState,
    onStateChange: (QuickCheckState) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        CriteriaCheckCard(
            values = state::value,
            onValueChange = { criterion, value -> onStateChange(state.with(criterion, value)) }
        )
        MapaCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                GroupLabel(
                    text = "ДОДАТКОВО · НЕ ВПЛИВАЄ НА СТАТУС",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
                CriterionRow(
                    title = "Паркування для МГН",
                    hint = "Місце для авто людей з інвалідністю",
                    value = state.accessibleParking,
                    onValueChange = { onStateChange(state.copy(accessibleParking = it)) },
                    note = null
                )
            }
        }
        OutlinedTextField(
            value = state.comment,
            onValueChange = { onStateChange(state.copy(comment = it)) },
            label = { Text("Коментар (необов'язково)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )
    }
}

@Composable
private fun GroupLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        color = color,
        modifier = modifier.semantics { heading() }
    )
}

private fun answerText(value: Boolean?) = when (value) {
    true -> "Так"
    false -> "Ні"
    null -> "Не вибрано"
}

/**
 * Рядок критерію. Для TalkBack текстова частина — один блок «Назва, підказка. Так / Ні / Не вибрано»,
 * а половинки перемикача — окремі радіокнопки в групі. Зі збільшеним шрифтом перемикач переходить
 * під текст на всю ширину, щоб нічого не обрізалось.
 */
@Composable
private fun CriterionRow(
    title: String,
    hint: String,
    value: Boolean?,
    onValueChange: ((Boolean) -> Unit)?,
    note: CriterionNote?
) {
    // Враховує і системний масштаб шрифту, і «Великий текст» у налаштуваннях застосунку.
    val textScale = LocalDensity.current.fontScale * MaterialTheme.typography.bodyLarge.fontSize.value / 16f
    val stacked = textScale >= 1.3f
    val description = buildString {
        append(title).append(", ").append(hint.replaceFirstChar { it.lowercase() }).append(". ")
        append(answerText(value))
        if (note != null) append(". ").append(if (note.fromAi) "Пропозиція ШІ: " else "").append(note.text)
    }
    val text: @Composable (Modifier) -> Unit = { textModifier ->
        Column(modifier = textModifier.semantics(mergeDescendants = true) { contentDescription = description }) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (note != null) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 6.dp)) {
                    if (note.fromAi) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp, end = 6.dp).size(14.dp)
                        )
                    }
                    Text(
                        text = note.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    if (stacked) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            text(Modifier.fillMaxWidth())
            YesNoToggle(title, value, onValueChange, Modifier.fillMaxWidth().padding(top = 10.dp))
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            text(Modifier.weight(1f))
            YesNoToggle(title, value, onValueChange, Modifier.widthIn(min = 128.dp).width(IntrinsicSize.Max))
        }
    }
}

/** Сегментований перемикач «Так | Ні» з двох половинок; null — нічого не вибрано. */
@Composable
private fun YesNoToggle(
    criterionTitle: String,
    value: Boolean?,
    onValueChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(shape)
            .border(1.dp, outline, shape)
            .selectableGroup()
    ) {
        ToggleHalf(criterionTitle, "Так", selected = value == true, selectedColor = CriterionYes, onClick = onValueChange?.let { { it(true) } }, modifier = Modifier.weight(1f))
        Box(Modifier.width(1.dp).fillMaxHeight().background(outline))
        ToggleHalf(criterionTitle, "Ні", selected = value == false, selectedColor = CriterionNo, onClick = onValueChange?.let { { it(false) } }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ToggleHalf(
    criterionTitle: String,
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val spec = if (reducedMotion) snap<Color>() else tween(durationMillis = 180)
    val background by animateColorAsState(if (selected) selectedColor else Color.Transparent, spec, label = "toggleBg")
    val content by animateColorAsState(
        if (selected) OnCriterionChoice else MaterialTheme.colorScheme.onSurfaceVariant,
        spec,
        label = "toggleText"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(background)
            .selectable(
                selected = selected,
                enabled = onClick != null,
                role = Role.RadioButton,
                onClick = { onClick?.invoke() }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            // selectable об'єднує це з роллю й станом: TalkBack читає «Пандус: Так, вибрано».
            modifier = Modifier.semantics { contentDescription = "$criterionTitle: $label" }
        )
    }
}
