package com.academy.mapainkluzyvnosti.ui.screens.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import com.academy.mapainkluzyvnosti.ui.components.liquidGlass
import com.academy.mapainkluzyvnosti.ui.state.TutorialCoordinator
import com.academy.mapainkluzyvnosti.ui.state.TutorialMode
import com.academy.mapainkluzyvnosti.ui.theme.AccentPrimary
import com.academy.mapainkluzyvnosti.ui.theme.AccentSecondary
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Інструктаж «Як користуватися»: повноекранний HorizontalPager зі свайпом, крапки-індикатор,
 * «Пропустити» вгорі, «Далі» / «Зрозуміло» внизу. Будь-який вихід (включно з «Назад») позначає
 * інструктаж переглянутим, щоб він не з'являвся при кожному вході.
 */
@Composable
fun TutorialScreen(
    mode: TutorialMode,
    onFinished: () -> Unit,
    coordinator: TutorialCoordinator = koinInject()
) {
    val slides = tutorialSlides(mode)
    val pagerState = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val hazeState = rememberHazeState()
    val isLast = pagerState.currentPage == slides.lastIndex

    fun finish() {
        scope.launch {
            coordinator.markSeen(mode)
            onFinished()
        }
    }
    BackHandler(onBack = ::finish)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Фон для «скла»: м'які кольорові плями дизайн-системи.
        Canvas(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
            drawCircle(
                Brush.radialGradient(listOf(AccentPrimary.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.2f), radius = size.width * 0.7f),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.15f, size.height * 0.2f)
            )
            drawCircle(
                Brush.radialGradient(listOf(AccentSecondary.copy(alpha = 0.28f), Color.Transparent), center = Offset(size.width * 0.9f, size.height * 0.75f), radius = size.width * 0.6f),
                radius = size.width * 0.6f,
                center = Offset(size.width * 0.9f, size.height * 0.75f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Як користуватися",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
                if (!isLast) {
                    TextButton(
                        onClick = ::finish,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 48.dp)
                            .semantics { contentDescription = "Пропустити інструктаж" }
                    ) {
                        Text("Пропустити", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                SlidePage(
                    slide = slides[page],
                    isCurrent = pagerState.currentPage == page,
                    hazeState = hazeState
                )
            }

            PageIndicator(
                count = slides.size,
                current = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp)
            )

            PrimaryButton(
                text = if (isLast) "Зрозуміло" else "Далі",
                onClick = {
                    if (isLast) finish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun SlidePage(slide: TutorialSlide, isCurrent: Boolean, hazeState: dev.chrisbanes.haze.HazeState) {
    // Прокрутка всередині слайда: зі збільшеним шрифтом текст не обрізається.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        TutorialIllustrationView(slide = slide, isCurrent = isCurrent)
        Column(
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .liquidGlass(hazeState, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Text(
                text = slide.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = slide.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Spacer(Modifier.size(8.dp))
    }
}

/** Крапки-індикатор; TalkBack чує «Слайд N з M» при кожній зміні сторінки. */
@Composable
private fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Слайд ${current + 1} з $count"
            liveRegion = LiveRegionMode.Polite
        }
    ) {
        repeat(count) { index ->
            val active = index == current
            Box(
                modifier = Modifier
                    .size(width = if (active) 22.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
            )
        }
    }
}
