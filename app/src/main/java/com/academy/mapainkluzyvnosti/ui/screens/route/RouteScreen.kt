package com.academy.mapainkluzyvnosti.ui.screens.route

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.academy.mapainkluzyvnosti.data.local.DeviceLocation
import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.domain.usecase.FindBarriersOnRoute
import com.academy.mapainkluzyvnosti.ui.components.MapaCard
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import com.academy.mapainkluzyvnosti.ui.components.ScreenHeader
import com.academy.mapainkluzyvnosti.ui.components.SosHelpButton
import com.academy.mapainkluzyvnosti.ui.components.formatDistanceKm
import com.academy.mapainkluzyvnosti.ui.components.formatDurationMinutes
import com.academy.mapainkluzyvnosti.ui.components.renderRoutePointDrawable
import com.academy.mapainkluzyvnosti.ui.maplibre.BARRIER_MARKER_IMAGE_ID
import com.academy.mapainkluzyvnosti.ui.maplibre.MapStyles
import com.academy.mapainkluzyvnosti.ui.maplibre.awaitMap
import com.academy.mapainkluzyvnosti.ui.maplibre.districtBounds
import com.academy.mapainkluzyvnosti.ui.maplibre.ensure3dBuildings
import com.academy.mapainkluzyvnosti.ui.maplibre.ensureDistrictLayers
import com.academy.mapainkluzyvnosti.ui.maplibre.registerMarkerImages
import com.academy.mapainkluzyvnosti.ui.maplibre.rememberMapLibreMapView
import com.academy.mapainkluzyvnosti.ui.maplibre.setDistrictBoundary
import com.academy.mapainkluzyvnosti.ui.theme.AccentPrimary
import com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point

private val SHEVCHENKIVSKYI_CENTER = LatLng(50.4550, 30.4850)
private const val ROUTE_SOURCE_ID = "mapa-route-line-source"
private const val ROUTE_CASING_LAYER_ID = "mapa-route-casing-layer"
private const val ROUTE_LAYER_ID = "mapa-route-line-layer"
private const val BARRIER_SOURCE_ID = "mapa-route-barrier-source"
private const val BARRIER_LAYER_ID = "mapa-route-barrier-layer"
private const val ROUTE_START_IMAGE_ID = "mapa-route-start"
private const val ROUTE_END_IMAGE_ID = "mapa-route-end"
private const val NAVIGATION_ZOOM = 17.0

private val RouteStartColor = Color(0xFF14B8A6)
private val RouteEndColor = Color(0xFFFF4D7D)

/** Екран 5: побудова маршруту. Поля «Моє місце» / заклад, мапа з лінією, панель з відстанню, часом і бар'єрами. */
@Composable
fun RouteScreen(
    destinationPlaceId: String,
    onBack: () -> Unit,
    onOpenSos: () -> Unit,
    viewModel: RouteViewModel = koinViewModel(parameters = { parametersOf(destinationPlaceId) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val districtBoundary by viewModel.districtBoundary.collectAsState()
    val favoriteRoutes by viewModel.favoriteRoutes.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val mapView = rememberMapLibreMapView()
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapStyle by remember { mutableStateOf<Style?>(null) }
    var isNavigating by remember { mutableStateOf(false) }

    // Головний флоу: «Будувати маршрут» з картки місця має одразу знати «звідки» (GPS)
    // і «куди» (обране місце) — без ручного вводу двох адрес.
    fun locateUser() {
        viewModel.onLocatingStarted()
        scope.launch {
            val point = DeviceLocation.getCurrent(context)
            if (point != null) viewModel.setCurrentLocation(point.lat, point.lng) else viewModel.onLocationDenied()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locateUser() else viewModel.onLocationDenied()
    }

    fun requestLocation() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) locateUser() else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    LaunchedEffect(Unit) { requestLocation() }

    LaunchedEffect(Unit) {
        val map = mapView.awaitMap()
        maplibreMap = map
        map.cameraPosition = CameraPosition.Builder()
            .target(SHEVCHENKIVSKYI_CENTER)
            .zoom(13.5)
            .tilt(0.0)
            .build()
        map.uiSettings.isRotateGesturesEnabled = false
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isAttributionEnabled = false
    }

    LaunchedEffect(maplibreMap) {
        val map = maplibreMap ?: return@LaunchedEffect
        map.setStyle(MapStyles.forTheme(isDarkTheme = true)) { loadedStyle ->
            loadedStyle.ensure3dBuildings()
            loadedStyle.registerMarkerImages(context)
            if (loadedStyle.getImage(ROUTE_START_IMAGE_ID) == null) {
                loadedStyle.addImage(ROUTE_START_IMAGE_ID, renderRoutePointDrawable(context, RouteStartColor))
            }
            if (loadedStyle.getImage(ROUTE_END_IMAGE_ID) == null) {
                loadedStyle.addImage(ROUTE_END_IMAGE_ID, renderRoutePointDrawable(context, RouteEndColor))
            }
            loadedStyle.ensureDistrictLayers()
            if (loadedStyle.getSource(ROUTE_SOURCE_ID) == null) {
                loadedStyle.addSource(GeoJsonSource(ROUTE_SOURCE_ID))
                loadedStyle.addLayer(
                    LineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                        PropertyFactory.lineColor(android.graphics.Color.WHITE),
                        PropertyFactory.lineWidth(9f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
                )
                loadedStyle.addLayer(
                    LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                        PropertyFactory.lineColor(AccentPrimary.toArgb()),
                        PropertyFactory.lineWidth(5.5f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
                )
            }
            // Ділянки з бар'єрами підсвічуються червоним поверх лінії маршруту.
            if (loadedStyle.getSource(BARRIER_SOURCE_ID) == null) {
                loadedStyle.addSource(GeoJsonSource(BARRIER_SOURCE_ID))
                loadedStyle.addLayer(
                    LineLayer(BARRIER_LAYER_ID, BARRIER_SOURCE_ID).withProperties(
                        PropertyFactory.lineColor(StatusBarrier.toArgb()),
                        PropertyFactory.lineWidth(7f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
                )
            }
            mapStyle = loadedStyle
        }
    }

    LaunchedEffect(mapStyle, districtBoundary) {
        val style = mapStyle ?: return@LaunchedEffect
        style.setDistrictBoundary(districtBoundary)
        districtBounds(districtBoundary)?.let { bounds -> maplibreMap?.setLatLngBoundsForCameraTarget(bounds) }
    }

    LaunchedEffect(mapStyle, uiState.route, uiState.barrierPlaces) {
        val style = mapStyle ?: return@LaunchedEffect
        val route = uiState.route
        val routeSource = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
        val barrierSource = style.getSourceAs<GeoJsonSource>(BARRIER_SOURCE_ID)
        if (route != null && route.geometry.size >= 2) {
            routeSource?.setGeoJson(LineString.fromLngLats(route.geometry.map { Point.fromLngLat(it.lng, it.lat) }))
            val segments = FindBarriersOnRoute.highlightSegments(route, uiState.barrierPlaces)
            if (segments.isEmpty()) {
                barrierSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            } else {
                val lines = segments.map { segment -> segment.map { Point.fromLngLat(it.lng, it.lat) } }
                barrierSource?.setGeoJson(Feature.fromGeometry(MultiLineString.fromLngLats(lines)))
            }
        } else {
            routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            barrierSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    // Увесь маршрут у кадрі, щойно він побудований.
    LaunchedEffect(maplibreMap, uiState.route) {
        val map = maplibreMap ?: return@LaunchedEffect
        isNavigating = false
        val route = uiState.route ?: return@LaunchedEffect
        fitRoute(map, route.geometry, with(density) { 170.dp.roundToPx() }, with(density) { 60.dp.roundToPx() })
    }

    val symbolManager = remember(mapStyle) {
        val map = maplibreMap
        val style = mapStyle
        if (map != null && style != null) SymbolManager(mapView, map, style) else null
    }

    LaunchedEffect(symbolManager, uiState.route, uiState.barrierPlaces) {
        val manager = symbolManager ?: return@LaunchedEffect
        manager.deleteAll()
        val route = uiState.route
        val options = buildList {
            uiState.barrierPlaces.forEach { place ->
                add(
                    SymbolOptions()
                        .withLatLng(LatLng(place.lat, place.lng))
                        .withIconImage(BARRIER_MARKER_IMAGE_ID)
                )
            }
            if (route != null) {
                add(SymbolOptions().withLatLng(LatLng(route.from.lat, route.from.lng)).withIconImage(ROUTE_START_IMAGE_ID))
                add(SymbolOptions().withLatLng(LatLng(route.to.lat, route.to.lng)).withIconImage(ROUTE_END_IMAGE_ID))
            }
        }
        if (options.isNotEmpty()) manager.create(options)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Побудова маршруту", onBack = onBack, onClose = onBack)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                RouteInputs(
                    fromValue = uiState.fromQuery,
                    toValue = uiState.toQuery,
                    isLocating = uiState.isLocatingUser,
                    isDestinationLocked = uiState.isDestinationLocked,
                    onFromChange = viewModel::onFromQueryChange,
                    onToChange = viewModel::onToQueryChange,
                    onLocate = ::requestLocation,
                    onSearch = viewModel::findRoute
                )
                if (uiState.locationDenied) {
                    Text(
                        text = "Не вдалося визначити місцезнаходження — введи адресу вручну",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            SosHelpButton(
                onClick = onOpenSos,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            )
        }

        RoutePanel(
            route = uiState.route,
            barrierPlaces = uiState.barrierPlaces,
            isLoading = uiState.isLoading,
            errorMessage = uiState.errorMessage,
            isFavorite = uiState.route?.let { route -> favoriteRoutes.any { it.id == route.id } } == true,
            isNavigating = isNavigating,
            onFindRoute = viewModel::findRoute,
            onToggleFavorite = viewModel::toggleFavoriteRoute,
            onStart = {
                val map = maplibreMap
                val route = uiState.route
                if (map != null && route != null) {
                    if (isNavigating) {
                        fitRoute(map, route.geometry, with(density) { 170.dp.roundToPx() }, with(density) { 60.dp.roundToPx() })
                    } else {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(route.from.lat, route.from.lng), NAVIGATION_ZOOM)
                        )
                    }
                    isNavigating = !isNavigating
                }
            }
        )
    }
}

private fun fitRoute(map: MapLibreMap, geometry: List<GeoPoint>, topPaddingPx: Int, sidePaddingPx: Int) {
    if (geometry.size < 2) return
    val bounds = LatLngBounds.Builder().apply { geometry.forEach { include(LatLng(it.lat, it.lng)) } }.build()
    map.easeCamera(
        CameraUpdateFactory.newLatLngBounds(bounds, sidePaddingPx, topPaddingPx, sidePaddingPx, sidePaddingPx)
    )
}

/** Дві точки маршруту: «Моє місце» (синя крапка) і пункт призначення (рожева мітка). */
@Composable
private fun RouteInputs(
    fromValue: String,
    toValue: String,
    isLocating: Boolean,
    isDestinationLocked: Boolean,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onLocate: () -> Unit,
    onSearch: () -> Unit
) {
    MapaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp).heightIn(min = 52.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            RouteTextField(
                value = fromValue,
                placeholder = "Моє місце",
                onValueChange = onFromChange,
                onSearch = onSearch,
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
            )
            if (isLocating) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(12.dp).size(24.dp))
            } else {
                IconButton(onClick = onLocate) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = "Моє місце",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 42.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 11.dp, end = 14.dp).heightIn(min = 52.dp)
        ) {
            Icon(Icons.Filled.Place, contentDescription = null, tint = RouteEndColor, modifier = Modifier.size(20.dp))
            RouteTextField(
                value = toValue,
                placeholder = "Куди",
                onValueChange = onToChange,
                onSearch = onSearch,
                readOnly = isDestinationLocked,
                modifier = Modifier.weight(1f).padding(start = 13.dp)
            )
        }
    }
}

@Composable
private fun RouteTextField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false
) {
    val textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Нижня панель: відстань і час, бар'єри на шляху, кнопка «Почати». */
@Composable
private fun RoutePanel(
    route: com.academy.mapainkluzyvnosti.data.model.Route?,
    barrierPlaces: List<Place>,
    isLoading: Boolean,
    errorMessage: String?,
    isFavorite: Boolean,
    isNavigating: Boolean,
    onFindRoute: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStart: () -> Unit
) {
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        if (route == null) {
            PrimaryButton(text = "Побудувати маршрут", onClick = onFindRoute, loading = isLoading)
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Straighten, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(
                text = formatDistanceKm(route.distanceMeters),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp, end = 20.dp)
            )
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(
                text = formatDurationMinutes(route.durationSeconds),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Зберегти маршрут",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (barrierPlaces.isNotEmpty()) {
            BarrierList(barrierPlaces = barrierPlaces, modifier = Modifier.padding(top = 12.dp))
        }

        PrimaryButton(
            text = if (isNavigating) "Завершити" else "Почати",
            onClick = onStart,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

/** Перелік бар'єрів на шляху (червоним) — ті самі, що підсвічені на лінії маршруту. */
@Composable
private fun BarrierList(barrierPlaces: List<Place>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusBarrier, modifier = Modifier.size(20.dp))
            Text(
                text = "Бар'єрів на шляху: ${barrierPlaces.size}",
                style = MaterialTheme.typography.titleSmall,
                color = StatusBarrier,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 120.dp).padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(barrierPlaces, key = { it.id }) { place ->
                Text(
                    text = "• ${place.name}" + place.address.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
