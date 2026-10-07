package com.academy.mapainkluzyvnosti.ui.screens.route

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled._3dRotation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.academy.mapainkluzyvnosti.data.local.DeviceLocation
import com.academy.mapainkluzyvnosti.ui.components.SosHelpButton
import com.academy.mapainkluzyvnosti.ui.components.renderRoutePointDrawable
import com.academy.mapainkluzyvnosti.ui.maplibre.MapStyles
import com.academy.mapainkluzyvnosti.ui.maplibre.awaitMap
import com.academy.mapainkluzyvnosti.ui.maplibre.ensure3dBuildings
import com.academy.mapainkluzyvnosti.ui.maplibre.placeImageId
import com.academy.mapainkluzyvnosti.ui.maplibre.registerMarkerImages
import com.academy.mapainkluzyvnosti.ui.maplibre.rememberMapLibreMapView
import com.academy.mapainkluzyvnosti.ui.state.ThemeStore
import com.academy.mapainkluzyvnosti.ui.theme.AccentPrimary
import com.academy.mapainkluzyvnosti.ui.theme.StatusAccessible
import com.academy.mapainkluzyvnosti.ui.theme.StatusBarrier
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private val SHEVCHENKIVSKYI_CENTER = LatLng(50.4550, 30.4850)
private const val ROUTE_SOURCE_ID = "mapa-route-line-source"
private const val ROUTE_LAYER_ID = "mapa-route-line-layer"
private const val BOUNDARY_SOURCE_ID = "mapa-district-dim-source"
private const val BOUNDARY_LAYER_ID = "mapa-district-dim-layer"
private const val ROUTE_START_IMAGE_ID = "mapa-route-start"
private const val ROUTE_END_IMAGE_ID = "mapa-route-end"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(
    destinationPlaceId: String,
    onBack: () -> Unit,
    onOpenSos: () -> Unit,
    viewModel: RouteViewModel = koinViewModel(parameters = { parametersOf(destinationPlaceId) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val districtBoundary by viewModel.districtBoundary.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeStore = koinInject<ThemeStore>()
    val isDarkTheme by themeStore.isDarkTheme.collectAsState()

    val mapView = rememberMapLibreMapView()
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapStyle by remember { mutableStateOf<Style?>(null) }
    var is3d by remember { mutableStateOf(true) }

    // Головний флоу: "Як дійти" з картки місця має одразу знати "звідки" (GPS)
    // і "куди" (обране місце) — без ручного вводу двох адрес.
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch {
                val point = DeviceLocation.getCurrent(context)
                if (point != null) viewModel.setCurrentLocation(point.lat, point.lng) else viewModel.onLocationDenied()
            }
        } else {
            viewModel.onLocationDenied()
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onLocatingStarted()
            val point = DeviceLocation.getCurrent(context)
            if (point != null) viewModel.setCurrentLocation(point.lat, point.lng) else viewModel.onLocationDenied()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(Unit) {
        val map = mapView.awaitMap()
        maplibreMap = map
        map.cameraPosition = CameraPosition.Builder()
            .target(SHEVCHENKIVSKYI_CENTER)
            .zoom(14.0)
            .tilt(50.0)
            .build()
    }

    LaunchedEffect(isDarkTheme, maplibreMap) {
        val map = maplibreMap ?: return@LaunchedEffect
        map.setStyle(MapStyles.forTheme(isDarkTheme)) { loadedStyle ->
            loadedStyle.ensure3dBuildings()
            loadedStyle.registerMarkerImages(context)
            if (loadedStyle.getImage(ROUTE_START_IMAGE_ID) == null) {
                loadedStyle.addImage(ROUTE_START_IMAGE_ID, renderRoutePointDrawable(context, StatusAccessible))
            }
            if (loadedStyle.getImage(ROUTE_END_IMAGE_ID) == null) {
                loadedStyle.addImage(ROUTE_END_IMAGE_ID, renderRoutePointDrawable(context, AccentPrimary))
            }
            if (loadedStyle.getSource(BOUNDARY_SOURCE_ID) == null) {
                loadedStyle.addSource(GeoJsonSource(BOUNDARY_SOURCE_ID))
                loadedStyle.addLayer(
                    FillLayer(BOUNDARY_LAYER_ID, BOUNDARY_SOURCE_ID).withProperties(
                        PropertyFactory.fillColor(android.graphics.Color.BLACK),
                        PropertyFactory.fillOpacity(0.35f)
                    )
                )
            }
            if (loadedStyle.getSource(ROUTE_SOURCE_ID) == null) {
                loadedStyle.addSource(GeoJsonSource(ROUTE_SOURCE_ID))
                loadedStyle.addLayer(
                    LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                        PropertyFactory.lineColor(AccentPrimary.toArgb()),
                        PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
                )
            }
            mapStyle = loadedStyle
        }
    }

    // Донат-полігон: великий зовнішній прямокутник з "дірою" по контуру району — затемнює все поза ним.
    LaunchedEffect(mapStyle, districtBoundary) {
        val style = mapStyle ?: return@LaunchedEffect
        val source = style.getSourceAs<GeoJsonSource>(BOUNDARY_SOURCE_ID) ?: return@LaunchedEffect
        val districtRing = districtBoundary.firstOrNull()?.map { Point.fromLngLat(it.lng, it.lat) }
        if (districtRing != null && districtRing.size >= 3) {
            val outerRing = listOf(
                Point.fromLngLat(-180.0, -85.0),
                Point.fromLngLat(-180.0, 85.0),
                Point.fromLngLat(180.0, 85.0),
                Point.fromLngLat(180.0, -85.0),
                Point.fromLngLat(-180.0, -85.0)
            )
            val closedDistrictRing = if (districtRing.first() == districtRing.last()) districtRing else districtRing + districtRing.first()
            source.setGeoJson(Polygon.fromLngLats(listOf(outerRing, closedDistrictRing)))
        } else {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    LaunchedEffect(mapStyle, uiState.route) {
        val style = mapStyle ?: return@LaunchedEffect
        val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID) ?: return@LaunchedEffect
        val route = uiState.route
        if (route != null && route.geometry.size >= 2) {
            source.setGeoJson(LineString.fromLngLats(route.geometry.map { Point.fromLngLat(it.lng, it.lat) }))
        } else {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    LaunchedEffect(maplibreMap, uiState.route) {
        val map = maplibreMap ?: return@LaunchedEffect
        uiState.route?.let { route ->
            map.easeCamera(CameraUpdateFactory.newLatLng(LatLng(route.from.lat, route.from.lng)))
        }
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
            if (route != null) {
                add(SymbolOptions().withLatLng(LatLng(route.from.lat, route.from.lng)).withIconImage(ROUTE_START_IMAGE_ID))
                add(SymbolOptions().withLatLng(LatLng(route.to.lat, route.to.lng)).withIconImage(ROUTE_END_IMAGE_ID))
            }
            uiState.barrierPlaces.forEach { place ->
                add(
                    SymbolOptions()
                        .withLatLng(LatLng(place.lat, place.lng))
                        .withIconImage(placeImageId(place.category, place.status))
                )
            }
        }
        if (options.isNotEmpty()) manager.create(options)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Маршрут") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = uiState.fromQuery,
                    onValueChange = viewModel::onFromQueryChange,
                    label = { Text("Звідки") },
                    trailingIcon = {
                        if (uiState.isLocatingUser) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (uiState.locationDenied) {
                    Text(
                        text = "Не вдалося визначити місцезнаходження — введи адресу вручну",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                OutlinedTextField(
                    value = uiState.toQuery,
                    onValueChange = viewModel::onToQueryChange,
                    label = { Text("Куди") },
                    readOnly = uiState.isDestinationLocked,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Button(
                    onClick = viewModel::findRoute,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text("Побудувати маршрут")
                }
                SosHelpButton(
                    onClick = onOpenSos,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            uiState.route?.let { route ->
                val favoriteRoutes by viewModel.favoriteRoutes.collectAsState()
                val isFavorite = favoriteRoutes.any { it.id == route.id }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "%.1f км · %d хв".format(route.distanceMeters / 1000, (route.durationSeconds / 60).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.barrierPlaces.isNotEmpty()) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusBarrier, modifier = Modifier.padding(end = 4.dp))
                        Text(
                            text = "Бар'єрів на шляху: ${uiState.barrierPlaces.size}",
                            color = StatusBarrier,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = viewModel::toggleFavoriteRoute) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Зберегти маршрут",
                            tint = if (isFavorite) AccentPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

                FloatingActionButton(
                    onClick = {
                        is3d = !is3d
                        val map = maplibreMap
                        if (map != null) {
                            val target = CameraPosition.Builder(map.cameraPosition)
                                .tilt(if (is3d) 50.0 else 0.0)
                                .build()
                            map.easeCamera(CameraUpdateFactory.newCameraPosition(target))
                        }
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(
                        Icons.Filled._3dRotation,
                        contentDescription = if (is3d) "Вимкнути 3D" else "Увімкнути 3D",
                        tint = if (is3d) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
