package com.academy.mapainkluzyvnosti.ui.screens.sos

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import com.academy.mapainkluzyvnosti.data.model.SosProblemType
import com.academy.mapainkluzyvnosti.ui.components.MapaCard
import com.academy.mapainkluzyvnosti.ui.components.MapaChip
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import com.academy.mapainkluzyvnosti.ui.components.SecondaryButton
import com.academy.mapainkluzyvnosti.ui.components.SosHelpButton
import com.academy.mapainkluzyvnosti.ui.components.SosQuickProblems
import com.academy.mapainkluzyvnosti.ui.components.label
import com.academy.mapainkluzyvnosti.ui.components.sosLocationLabel
import com.academy.mapainkluzyvnosti.ui.maplibre.MapStyles
import com.academy.mapainkluzyvnosti.ui.maplibre.awaitMap
import com.academy.mapainkluzyvnosti.ui.maplibre.rememberMapLibreMapView
import com.academy.mapainkluzyvnosti.ui.screens.map.CenterPin
import com.academy.mapainkluzyvnosti.ui.state.SosPhase
import com.academy.mapainkluzyvnosti.ui.state.SosState
import com.academy.mapainkluzyvnosti.ui.theme.StatusAccessible
import com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng

private val SHEVCHENKIVSKYI_CENTER = GeoPoint(50.4550, 30.4850)

/**
 * Екран SOS. Сам запит уже надіслано утриманням кнопки (нижня панель / маршрут) — тут лише стан:
 * «Запит надіслано» з необов'язковим «Що сталося?», «Уточнити, де я» і «Скасувати запит»;
 * без мережі — чітке «НЕ надіслано» й велика кнопка 112. Якщо запиту немає (відкрили з профілю) —
 * велика кнопка SOS з утриманням.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosRequestScreen(onDone: () -> Unit, onRequestLogin: () -> Unit, viewModel: SosRequestViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val manualPick by viewModel.manualPick.collectAsState()

    if (manualPick.isOpen) {
        BackHandler { viewModel.closeManualPick() }
        ManualLocationPicker(
            pick = manualPick,
            initial = state.location?.point ?: manualPick.anchor ?: SHEVCHENKIVSKYI_CENTER,
            onChoose = viewModel::chooseManualLocation,
            onClose = viewModel::closeManualPick
        )
        return
    }

    fun close() {
        viewModel.reset()
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Допомога") },
                navigationIcon = {
                    IconButton(onClick = ::close) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (state.phase) {
                SosPhase.IDLE -> IdleContent(onSend = viewModel::send)
                SosPhase.NEEDS_LOGIN -> {
                    StatusCard(
                        title = "Щоб надіслати SOS, увійдіть в акаунт",
                        text = "Запит бачать волонтери, тож потрібен акаунт. У небезпеці телефонуйте 112.",
                        color = StatusBarrier
                    )
                    PrimaryButton(text = "Увійти", onClick = {
                        viewModel.reset()
                        onRequestLogin()
                    })
                    EmergencyCallButton(large = true)
                }
                SosPhase.SENDING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text(
                            text = "Надсилаємо запит…",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    EmergencyCallButton(large = false)
                }
                SosPhase.SENT, SosPhase.QUEUED -> ActiveRequestContent(
                    state = state,
                    onDetails = viewModel::setDetails,
                    onRefineLocation = viewModel::openManualPick,
                    onRetry = viewModel::retry,
                    onCancel = viewModel::cancel
                )
                SosPhase.CANCELLED -> {
                    StatusCard(
                        title = "Запит скасовано",
                        text = "Волонтери більше не бачать цей запит.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PrimaryButton(text = "Готово", onClick = ::close)
                }
            }
        }
    }
}

@Composable
private fun IdleContent(onSend: () -> Unit) {
    Text(
        text = "Утримуйте кнопку 1 секунду — запит одразу отримають волонтери, учні й адміністратори поруч. " +
            "Що сталося, можна буде додати після відправки.",
        style = MaterialTheme.typography.bodyLarge
    )
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        SosHelpButton(onHoldComplete = onSend, diameter = 140.dp, caption = "Утримуйте, щоб надіслати")
    }
    EmergencyCallButton(large = false)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveRequestContent(
    state: SosState,
    onDetails: (SosProblemType?, String) -> Unit,
    onRefineLocation: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val sent = state.phase == SosPhase.SENT
    if (sent) {
        StatusCard(
            title = "Запит надіслано",
            text = "Волонтери, учні й адміністратори бачать його на мапі. Не закривайте застосунок, якщо можете.",
            color = StatusAccessible,
            icon = Icons.Filled.CheckCircle
        )
    } else {
        // Не вдаємо, що запит пішов.
        StatusCard(
            title = "Запит НЕ надіслано",
            text = "${state.error ?: "Немає зв'язку"}. Запит збережено — надішлемо автоматично, щойно з'явиться мережа.",
            color = StatusBarrier,
            icon = Icons.Filled.CloudOff
        )
        EmergencyCallButton(large = true)
        SecondaryButton(text = "Спробувати зараз", onClick = onRetry)
    }
    if (sent && state.hasUnsyncedChanges) {
        Text(
            text = "Зміни ще не дійшли до сервера (${state.error ?: "немає зв'язку"}) — повторимо автоматично.",
            style = MaterialTheme.typography.bodySmall,
            color = StatusBarrier
        )
    }

    LocationSection(state = state, onRefine = onRefineLocation)

    // Що сталося — необов'язково; доповнює той самий запит.
    var selected by rememberSaveable { mutableStateOf(state.problemType) }
    var comment by rememberSaveable { mutableStateOf(state.comment) }
    val changed = selected != state.problemType || comment.trim() != state.comment.trim()
    Column {
        Text(
            text = "Що сталося? (необов'язково)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            SosQuickProblems.forEach { type ->
                MapaChip(
                    label = type.label(),
                    selected = selected == type,
                    onClick = { selected = if (selected == type) null else type }
                )
            }
        }
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it.take(500) },
            label = { Text("Коментар") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        PrimaryButton(
            text = if (sent) "Додати до запиту" else "Зберегти до запиту",
            onClick = { onDetails(selected, comment) },
            enabled = changed,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (!changed && (state.problemType != null || state.comment.isNotBlank()) && !state.hasUnsyncedChanges && sent) {
            Text(
                text = "Додано до запиту — волонтери бачать оновлення",
                style = MaterialTheme.typography.bodySmall,
                color = StatusAccessible,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    if (sent) EmergencyCallButton(large = false)
    TextButton(
        onClick = onCancel,
        enabled = !state.isCancelling,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (state.isCancelling) "Скасовуємо…" else "Скасувати запит",
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LocationSection(state: SosState, onRefine: () -> Unit) {
    MapaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = state.location?.let { sosLocationLabel(it.source, it.accuracyM, it.timeMs) }
                        ?: "Локація невідома",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp).weight(1f)
                )
                if (state.isLocating) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
            val hint = when {
                state.locationRejected ->
                    "Свіжа геолокація неправдоподібна (можливо, глушіння GPS) — її не використано."
                state.location == null ->
                    "Запит надіслано без локації. Вкажіть, де ви, — волонтерам так буде легше вас знайти."
                state.isLocating -> "Уточнюємо позицію…"
                else -> null
            }
            hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            SecondaryButton(
                text = "Уточнити, де я",
                icon = Icons.Filled.EditLocationAlt,
                onClick = onRefine,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    text: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = if (icon != null) 10.dp else 0.dp).semantics { heading() }
            )
        }
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

/** 112 — текстом і кнопкою дзвінка (відкриває набір номера; дзвінок підтверджує сама людина). */
@Composable
private fun EmergencyCallButton(large: Boolean) {
    val context = LocalContext.current
    val call = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    if (large) {
        Button(
            onClick = call,
            colors = ButtonDefaults.buttonColors(containerColor = StatusBarrier, contentColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
        ) {
            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(
                text = "  Подзвонити 112",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = call)
                .padding(vertical = 8.dp)
        ) {
            Icon(Icons.Filled.Call, contentDescription = null, tint = StatusBarrier)
            Text(
                text = "Екстрена допомога: 112",
                style = MaterialTheme.typography.titleSmall,
                color = StatusBarrier,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** «Уточнити, де я»: точка на мапі або найближчий заклад зі списку. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualLocationPicker(
    pick: ManualPickState,
    initial: GeoPoint,
    onChoose: (GeoPoint) -> Unit,
    onClose: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(if (pick.anchor != null) 1 else 0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Уточнити, де я") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("На мапі") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Заклад поруч") })
            }
            if (tab == 0) {
                MapPointPicker(initial = initial, onChoose = onChoose)
            } else {
                NearbyPlacesList(pick = pick, onChoose = onChoose)
            }
        }
    }
}

@Composable
private fun MapPointPicker(initial: GeoPoint, onChoose: (GeoPoint) -> Unit) {
    val mapView = rememberMapLibreMapView()
    var map by remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
    LaunchedEffect(Unit) {
        val m = mapView.awaitMap()
        m.cameraPosition = CameraPosition.Builder().target(LatLng(initial.lat, initial.lng)).zoom(16.0).build()
        m.uiSettings.isRotateGesturesEnabled = false
        m.uiSettings.isLogoEnabled = false
        m.uiSettings.isAttributionEnabled = false
        m.setStyle(MapStyles.forTheme(isDarkTheme = true))
        map = m
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        CenterPin()
        Text(
            text = "Посуньте мапу так, щоб шпилька була там, де ви",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        PrimaryButton(
            text = "Я тут",
            onClick = {
                map?.cameraPosition?.target?.let { onChoose(GeoPoint(it.latitude, it.longitude)) }
            },
            enabled = map != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).navigationBarsPadding()
        )
    }
}

@Composable
private fun NearbyPlacesList(pick: ManualPickState, onChoose: (GeoPoint) -> Unit) {
    when {
        pick.anchor == null -> CenteredNote("Немає останньої надійної позиції, щоб знайти заклади поруч. Позначте місце на мапі.")
        pick.isLoadingPlaces -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        pick.placesError != null -> CenteredNote(pick.placesError)
        pick.places.isEmpty() -> CenteredNote("Поруч немає закладів з бази. Позначте місце на мапі.")
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(pick.places, key = { it.place.id }) { nearby ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChoose(GeoPoint(nearby.place.lat, nearby.place.lng)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(text = nearby.place.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = listOf(nearby.place.address, "≈ ${nearby.distanceM} м").filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun CenteredNote(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
