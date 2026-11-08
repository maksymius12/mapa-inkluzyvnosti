package com.academy.mapainkluzyvnosti.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.data.repository.OwnCheck
import com.academy.mapainkluzyvnosti.ui.components.MapaCard
import com.academy.mapainkluzyvnosti.ui.components.StatusBadge
import com.academy.mapainkluzyvnosti.ui.components.formatCheckDate
import com.academy.mapainkluzyvnosti.ui.components.label
import com.academy.mapainkluzyvnosti.ui.components.rememberReducedMotion
import com.academy.mapainkluzyvnosti.ui.theme.StatusPartial

/** Як у сервері (тригер checks_after_insert_apply): +10 балів за кожну перевірку. */
private const val POINTS_PER_CHECK = 10

/** Значок — кожні 5 перевірок (єдина умова в проєкті; окремого переліку значків немає). */
private const val CHECKS_PER_BADGE = 5
private const val COLLAPSED_CHECKS = 5

fun badgeCount(checksCount: Int): Int = checksCount / CHECKS_PER_BADGE

/**
 * «Твій внесок»: підсумок (бали / перевірки / значки) — кнопка, що розкриває деталі під собою.
 * Деталі — лише реальні дані: значки за правилом «кожні 5 перевірок», як нараховуються бали,
 * власні перевірки (тап — картка місця), додані місця, відгуки, схвалені заявки.
 */
@Composable
fun ContributionCard(
    points: Int,
    checksCount: Int,
    state: ContributionState,
    onExpand: () -> Unit,
    onOpenPlace: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reducedMotion) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(200),
        label = "contribution-chevron"
    )
    val badges = badgeCount(checksCount)
    val toggle = {
        expanded = !expanded
        if (expanded) onExpand()
    }

    MapaCard(modifier = modifier.fillMaxWidth()) {
        // Підсумок — одна кнопка для TalkBack: «Твій внесок: 190 балів, 19 перевірок, 3 значки. Згорнуто».
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = toggle)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = "Твій внесок: ${plural(points, "бал", "бали", "балів")}, " +
                        "${plural(checksCount, "перевірка", "перевірки", "перевірок")}, ${plural(badges, "значок", "значки", "значків")}"
                    stateDescription = if (expanded) "Розгорнуто" else "Згорнуто"
                    onClick(label = if (expanded) "згорнути деталі" else "показати деталі") { toggle(); true }
                }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Твій внесок", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn(value = points.toString(), label = "Балів")
                StatColumn(value = checksCount.toString(), label = "Перевірок")
                StatColumn(value = badges.toString(), label = "Значків")
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = if (reducedMotion) EnterTransition.None else expandVertically() + fadeIn(),
            exit = if (reducedMotion) ExitTransition.None else shrinkVertically() + fadeOut()
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ContributionDetails(checksCount = checksCount, state = state, onRetry = onExpand, onOpenPlace = onOpenPlace)
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = Icons.Filled.EmojiEvents, contentDescription = null, tint = StatusPartial)
        Text(text = value, style = MaterialTheme.typography.titleLarge)
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ContributionDetails(checksCount: Int, state: ContributionState, onRetry: () -> Unit, onOpenPlace: (String) -> Unit) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        BadgesSection(checksCount)
        PointsSection(state)
        ChecksSection(state, onRetry, onOpenPlace)
        state.data?.let { data ->
            if (data.addedPlaces != null || data.reviews != null || data.approvedRequests != null) {
                Column {
                    SectionHeading("Ще зроблено")
                    data.addedPlaces?.let { CountLine("Додано місць", it) }
                    data.reviews?.let { CountLine("Відгуків", it) }
                    data.approvedRequests?.let { CountLine("Схвалених заявок", it) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp).semantics { heading() }
    )
}

@Composable
private fun BadgesSection(checksCount: Int) {
    val earned = badgeCount(checksCount)
    Column {
        SectionHeading("Значки")
        if (earned == 0) {
            Text(
                text = "Ще немає значків. Перевір перше місце!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        for (n in 1..earned) BadgeRow(milestone = n * CHECKS_PER_BADGE, earned = true, remaining = 0)
        // Наступний — приглушений, з тим, скільки ще треба.
        val next = (earned + 1) * CHECKS_PER_BADGE
        BadgeRow(milestone = next, earned = false, remaining = next - checksCount)
    }
}

@Composable
private fun BadgeRow(milestone: Int, earned: Boolean, remaining: Int) {
    val dim = if (earned) 1f else 0.55f
    val title = "Значок «$milestone перевірок»"
    val note = if (earned) "Отримано: перевірено ${plural(milestone, "місце", "місця", "місць")}" else "Ще $remaining ${checksWord(remaining)} до значка"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clearAndSetSemantics {
                contentDescription = "$title. ${if (earned) "Отримано" else "Ще не отримано"}. $note"
            }
            .graphicsLayer { alpha = dim }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (earned) StatusPartial.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                if (earned) Icons.Filled.EmojiEvents else Icons.Outlined.Lock,
                contentDescription = null,
                tint = if (earned) StatusPartial else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PointsSection(state: ContributionState) {
    Column {
        SectionHeading("Бали")
        Text(
            text = "+$POINTS_PER_CHECK за кожну перевірку місця. Інших нарахувань у застосунку поки немає.",
            style = MaterialTheme.typography.bodyMedium
        )
        val recent = state.data?.checks.orEmpty().take(COLLAPSED_CHECKS)
        if (recent.isNotEmpty()) {
            Text(
                text = "Останні нарахування",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
            recent.forEach { check ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}) {
                    Text(text = "+$POINTS_PER_CHECK", fontWeight = FontWeight.Bold, color = StatusPartial, modifier = Modifier.padding(end = 10.dp))
                    Text(
                        text = "Перевірка: ${check.placeName ?: "місце недоступне"}" +
                            (check.checkedAt?.let(::formatCheckDate)?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecksSection(state: ContributionState, onRetry: () -> Unit, onOpenPlace: (String) -> Unit) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    Column {
        SectionHeading("Перевірки")
        when {
            state.isLoading && state.data == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Завантажуємо…", modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
            }
            state.error != null && state.data == null -> Column {
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Спробувати ще раз") }
            }
            state.data?.checks.isNullOrEmpty() -> Text(
                text = "Ще немає перевірок. Знайди місце на мапі й натисни «Пройти перевірку».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> {
                val checks = state.data!!.checks
                val visible = if (showAll) checks else checks.take(COLLAPSED_CHECKS)
                visible.forEachIndexed { index, check ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CheckRow(check, onOpenPlace)
                }
                if (checks.size > COLLAPSED_CHECKS) {
                    TextButton(onClick = { showAll = !showAll }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (showAll) "Показати менше" else "Показати всі (${checks.size})")
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(check: OwnCheck, onOpenPlace: (String) -> Unit) {
    val date = check.checkedAt?.let(::formatCheckDate)
    val available = check.placeName != null
    val name = check.placeName ?: "Місце недоступне"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (available) it.clickable { onOpenPlace(check.placeId) } else it }
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(name)
                    date?.let { append(", перевірено $it") }
                    check.placeStatus?.let { append(", статус зараз: ${it.label()}") }
                }
                if (available) {
                    role = Role.Button
                    onClick(label = "відкрити картку місця") { onOpenPlace(check.placeId); true }
                }
            }
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            date?.let {
                Text(text = "Перевірено $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        check.placeStatus?.let { StatusBadge(status = it, modifier = Modifier.padding(start = 8.dp)) }
    }
}

@Composable
private fun CountLine(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(text = value.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

private fun checksWord(n: Int): String = pluralForm(n, "перевірка", "перевірки", "перевірок")

/** «1 бал», «3 бали», «5 балів». */
private fun plural(n: Int, one: String, few: String, many: String): String = "$n ${pluralForm(n, one, few, many)}"

private fun pluralForm(n: Int, one: String, few: String, many: String): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
}
