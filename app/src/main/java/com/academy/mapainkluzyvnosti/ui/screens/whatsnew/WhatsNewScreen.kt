package com.academy.mapainkluzyvnosti.ui.screens.whatsnew

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import com.academy.mapainkluzyvnosti.ui.components.rememberReducedMotion
import com.academy.mapainkluzyvnosti.ui.state.WhatsNewStore
import org.koin.compose.koinInject

/**
 * «Що нового» — один раз після оновлення. Будь-який вихід («Зрозуміло», хрестик, системне «Назад»)
 * запам'ятовує поточну версію, тож екран більше не з'явиться.
 */
@Composable
fun WhatsNewScreen(onClose: () -> Unit, store: WhatsNewStore = koinInject()) {
    val releases = remember { store.releasesToShow() }
    val items = releases.flatMap { it.items }
    val versionName = releases.firstOrNull()?.versionName

    fun close() {
        store.markSeen()
        onClose()
    }
    BackHandler(onBack = ::close)

    // Лише плавна поява (без мигання); із «Вимкнути анімацію» — одразу видно.
    val reducedMotion = rememberReducedMotion()
    val appear = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reducedMotion) appear.animateTo(1f, tween(durationMillis = 300)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp)
        ) {
            Text(
                text = if (versionName != null) "Що нового у версії $versionName" else "Що нового",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            IconButton(onClick = ::close, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Закрити «Що нового»", tint = MaterialTheme.colorScheme.onBackground)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = appear.value
                    translationY = (1f - appear.value) * 24.dp.toPx()
                }
        ) {
            items(items, key = { it.title }) { item -> WhatsNewRow(item) }
        }

        // Закріплена внизу — не прокручується разом зі списком.
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PrimaryButton(
            text = "Зрозуміло",
            onClick = ::close,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .semantics { contentDescription = "Зрозуміло, закрити «Що нового»" }
        )
    }
}

@Composable
private fun WhatsNewRow(item: WhatsNewItem) {
    // Один блок для TalkBack: «Назва. Опис».
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics(mergeDescendants = true) {}
            .padding(16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
