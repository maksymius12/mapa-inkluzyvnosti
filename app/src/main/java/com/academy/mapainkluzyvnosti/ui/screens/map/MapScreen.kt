package com.academy.mapainkluzyvnosti.ui.screens.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.academy.mapainkluzyvnosti.data.local.DeviceLocation
import com.academy.mapainkluzyvnosti.data.model.AccessibilityType
import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.model.markerType
import com.academy.mapainkluzyvnosti.ui.components.MapaCard
import com.academy.mapainkluzyvnosti.ui.components.MapaChip
import com.academy.mapainkluzyvnosti.ui.components.PlaceThumbnail
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import com.academy.mapainkluzyvnosti.ui.components.RatingRow
import com.academy.mapainkluzyvnosti.ui.components.SosHelpButton
import com.academy.mapainkluzyvnosti.ui.components.SosInfoCard
import com.academy.mapainkluzyvnosti.ui.components.StatusBadge
import com.academy.mapainkluzyvnosti.ui.components.visual
import com.academy.mapainkluzyvnosti.ui.maplibre.MapStyles
import com.academy.mapainkluzyvnosti.ui.maplibre.SOS_MARKER_IMAGE_ID
import com.academy.mapainkluzyvnosti.ui.maplibre.awaitMap
import com.academy.mapainkluzyvnosti.ui.maplibre.districtBounds
import com.academy.mapainkluzyvnosti.ui.maplibre.ensure3dBuildings
import com.academy.mapainkluzyvnosti.ui.maplibre.ensureDistrictLayers
import com.academy.mapainkluzyvnosti.ui.maplibre.ensureUserLocationLayer
import com.academy.mapainkluzyvnosti.ui.maplibre.registerMarkerImages
import com.academy.mapainkluzyvnosti.ui.maplibre.rememberMapLibreMapView
import com.academy.mapainkluzyvnosti.ui.maplibre.setDistrictBoundary
import com.academy.mapainkluzyvnosti.ui.maplibre.setUserLocation
import com.academy.mapainkluzyvnosti.ui.maplibre.typeImageId
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

private val SHEVCHENKIVSKYI_CENTER = LatLng(50.4550, 30.4850)
private const val DEFAULT_ZOOM = 13.5
private const val MY_LOCATION_ZOOM = 16.0
private const val SELECTED_ICON_SCALE = 1.3f

@Composable
fun MapScreen(
    onOpenPlace: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSos: () -> Unit,
    viewModel: MapViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isVolunteer by viewModel.isVolunteer.collectAsState()
    val selection by viewModel.filterSelection.collectAsState()
    val districtBoundary by viewModel.districtBoundary.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val mapView = rememberMapLibreMapView()
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapStyle by remember { mutableStateOf<Style?>(null) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }

    // Список місць і активних SOS не повинен переживати довше одного показу екрана:
    // щоразу, коли карта повертається на передній план (у т.ч. після відкриття іншої
    // вкладки й повернення), тягнемо свіжі дані з Supabase замість застояного
    // стану, що зберігся у ViewModel завдяки saveState/restoreState нижньої навігації.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun centerOnUser(point: GeoPoint) {
        maplibreMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(point.lat, point.lng), MY_LOCATION_ZOOM)
        )
    }

    suspend fun locateUser(moveCamera: Boolean) {
        val point = DeviceLocation.getCurrent(context)
        if (point == null) {
            if (moveCamera) snackbarHostState.showSnackbar("Не вдалося визначити місцезнаходження")
            return
        }
        userLocation = point
        if (moveCamera) centerOnUser(point)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch { locateUser(moveCamera = true) }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Дозвольте доступ до геолокації, щоб побачити себе на мапі") }
        }
    }

    // Початкова позиція камери — один раз, після готовності рушія.
    LaunchedEffect(Unit) {
        val map = mapView.awaitMap()
        maplibreMap = map
        map.cameraPosition = CameraPosition.Builder()
            .target(SHEVCHENKIVSKYI_CENTER)
            .zoom(DEFAULT_ZOOM)
            .tilt(0.0)
            .build()
        map.uiSettings.isRotateGesturesEnabled = false
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isAttributionEnabled = false
        map.setMinZoomPreference(10.5)
    }

    LaunchedEffect(maplibreMap) {
        val map = maplibreMap ?: return@LaunchedEffect
        map.setStyle(MapStyles.forTheme(isDarkTheme = true)) { loadedStyle ->
            loadedStyle.ensure3dBuildings()
            loadedStyle.registerMarkerImages(context)
            loadedStyle.ensureDistrictLayers()
            loadedStyle.ensureUserLocationLayer()
            mapStyle = loadedStyle
        }
    }

    // Мапа показує лише Шевченківський район: решта затемнена, камера обмежена його межами.
    LaunchedEffect(mapStyle, districtBoundary) {
        val style = mapStyle ?: return@LaunchedEffect
        style.setDistrictBoundary(districtBoundary)
        districtBounds(districtBoundary)?.let { bounds -> maplibreMap?.setLatLngBoundsForCameraTarget(bounds) }
    }

    LaunchedEffect(mapStyle) {
        if (mapStyle != null && DeviceLocation.hasPermission(context)) locateUser(moveCamera = false)
    }
    LaunchedEffect(mapStyle, userLocation) {
        mapStyle?.setUserLocation(userLocation)
    }

    // Тап по порожньому місцю мапи ховає нижню панель.
    DisposableEffect(maplibreMap) {
        val map = maplibreMap
        val listener = MapLibreMap.OnMapClickListener {
            viewModel.clearSelection()
            false
        }
        map?.addOnMapClickListener(listener)
        onDispose { map?.removeOnMapClickListener(listener) }
    }

    val placesSymbolManager = remember(mapStyle) {
        val map = maplibreMap
        val style = mapStyle
        if (map != null && style != null) {
            SymbolManager(mapView, map, style).apply {
                addClickListener { symbol ->
                    symbol.data?.asString?.let { viewModel.selectPlace(it) }
                    true
                }
            }
        } else {
            null
        }
    }

    val sosSymbolManager = remember(mapStyle) {
        val map = maplibreMap
        val style = mapStyle
        if (map != null && style != null) {
            SymbolManager(mapView, map, style).apply {
                addClickListener { symbol ->
                    symbol.data?.asString?.let { viewModel.selectSos(it) }
                    true
                }
            }
        } else {
            null
        }
    }

    // Мітка = тип доступності за легендою (з урахуванням активного чипа/фільтра).
    LaunchedEffect(placesSymbolManager, uiState.places, uiState.activeTypes, uiState.selectedPlaceId) {
        val manager = placesSymbolManager ?: return@LaunchedEffect
        manager.deleteAll()
        val options = uiState.places.map { place ->
            SymbolOptions()
                .withLatLng(LatLng(place.lat, place.lng))
                .withIconImage(typeImageId(place.markerType(uiState.activeTypes)))
                .withIconSize(if (place.id == uiState.selectedPlaceId) SELECTED_ICON_SCALE else 1f)
                .withData(JsonPrimitive(place.id))
        }
        if (options.isNotEmpty()) manager.create(options)
    }

    LaunchedEffect(sosSymbolManager, uiState.sosRequests) {
        val manager = sosSymbolManager ?: return@LaunchedEffect
        manager.deleteAll()
        val options = uiState.sosRequests.map { sos ->
            SymbolOptions()
                .withLatLng(LatLng(sos.lat, sos.lng))
                .withIconImage(SOS_MARKER_IMAGE_ID)
                .withData(JsonPrimitive(sos.id))
        }
        if (options.isNotEmpty()) manager.create(options)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    // Мапа починається під системним рядком стану, щоб його іконки читалися на темному фоні застосунку.
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // Верх: пошук, чипи-фільтри, кнопка фільтрів.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            SearchField(
                onClick = onOpenSearch,
                onLocate = {
                    if (DeviceLocation.hasPermission(context)) {
                        scope.launch { locateUser(moveCamera = true) }
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            TypeChips(
                activeTypes = selection.types,
                onSelect = viewModel::selectTypeChip,
                modifier = Modifier.padding(top = 10.dp)
            )
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                Spacer(Modifier.weight(1f))
                MapControlButton(onClick = onOpenFilters, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Filled.Tune, contentDescription = "Фільтри")
                }
            }
        }

        // Низ: SOS зліва, «моє місце» й масштаб справа, під ними — картка вибраного місця/SOS.
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                SosHelpButton(onClick = onOpenSos)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MapControlButton(
                        onClick = {
                            if (DeviceLocation.hasPermission(context)) {
                                scope.launch { locateUser(moveCamera = true) }
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        shape = CircleShape
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "Моє місце")
                    }
                    ZoomControls(
                        onZoomIn = { maplibreMap?.animateCamera(CameraUpdateFactory.zoomIn()) },
                        onZoomOut = { maplibreMap?.animateCamera(CameraUpdateFactory.zoomOut()) }
                    )
                }
            }

            uiState.selectedPlace?.let { place ->
                PlaceBottomCard(
                    place = place,
                    activeTypes = uiState.activeTypes,
                    onDetails = { onOpenPlace(place.id) },
                    onClose = viewModel::clearSelection,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }

            uiState.selectedSos?.let { sos ->
                SosInfoCard(
                    sos = sos,
                    isVolunteer = isVolunteer,
                    onResolve = viewModel::resolveSelectedSos,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp)
        )
    }
}

@Composable
private fun SearchField(onClick: () -> Unit, onLocate: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(start = 16.dp)
            .height(52.dp)
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "Пошук місць, адрес…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 12.dp)
        )
        IconButton(onClick = onLocate) {
            Icon(Icons.Filled.MyLocation, contentDescription = "Моє місце", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TypeChips(activeTypes: Set<AccessibilityType>, onSelect: (AccessibilityType?) -> Unit, modifier: Modifier = Modifier) {
    val allSelected = activeTypes.size == AccessibilityType.entries.size
    LazyRow(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { MapaChip(label = "Усі", selected = allSelected, onClick = { onSelect(null) }) }
        items(AccessibilityType.entries) { type ->
            MapaChip(
                label = type.visual.label,
                selected = !allSelected && activeTypes == setOf(type),
                onClick = { onSelect(type) }
            )
        }
    }
}

/** Плаваюча кнопка поверх мапи (фільтри, «моє місце»). */
@Composable
private fun MapControlButton(
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
    ) {
        content()
    }
}

@Composable
private fun ZoomControls(onZoomIn: () -> Unit, onZoomOut: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        IconButton(onClick = onZoomIn, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Збільшити")
        }
        HorizontalDivider(modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
        IconButton(onClick = onZoomOut, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Зменшити")
        }
    }
}

/** Коротка панель вибраного місця: назва, адреса, рейтинг, статус і кнопка «Детальніше». */
@Composable
private fun PlaceBottomCard(
    place: Place,
    activeTypes: Set<AccessibilityType>,
    onDetails: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    MapaCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                PlaceThumbnail(category = place.category, photoUrl = null, size = 48.dp)
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = place.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Закрити",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                StatusBadge(status = place.status)
                RatingRow(rating = place.rating)
                place.markerType(activeTypes)?.let { type ->
                    Text(
                        text = type.visual.singularLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = type.visual.color
                    )
                }
            }
            PrimaryButton(text = "Детальніше", onClick = onDetails, modifier = Modifier.padding(top = 14.dp))
        }
    }
}
