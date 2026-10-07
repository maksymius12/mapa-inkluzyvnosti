package com.academy.mapainkluzyvnosti.ui.screens.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled._3dRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.academy.mapainkluzyvnosti.ui.components.PlaceCard
import com.academy.mapainkluzyvnosti.ui.components.SosHelpButton
import com.academy.mapainkluzyvnosti.ui.components.SosInfoCard
import com.academy.mapainkluzyvnosti.ui.maplibre.MapStyles
import com.academy.mapainkluzyvnosti.ui.maplibre.SOS_MARKER_IMAGE_ID
import com.academy.mapainkluzyvnosti.ui.maplibre.awaitMap
import com.academy.mapainkluzyvnosti.ui.maplibre.ensure3dBuildings
import com.academy.mapainkluzyvnosti.ui.maplibre.placeImageId
import com.academy.mapainkluzyvnosti.ui.maplibre.registerMarkerImages
import com.academy.mapainkluzyvnosti.ui.maplibre.rememberMapLibreMapView
import com.academy.mapainkluzyvnosti.ui.state.ThemeStore
import com.google.gson.JsonPrimitive
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

private val SHEVCHENKIVSKYI_CENTER = LatLng(50.4550, 30.4850)

@OptIn(ExperimentalMaterial3Api::class)
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val themeStore = koinInject<ThemeStore>()
    val isDarkTheme by themeStore.isDarkTheme.collectAsState()

    val mapView = rememberMapLibreMapView()
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapStyle by remember { mutableStateOf<Style?>(null) }
    var is3d by remember { mutableStateOf(true) }

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

    // Початкова позиція камери — один раз, після готовності рушія.
    LaunchedEffect(Unit) {
        val map = mapView.awaitMap()
        maplibreMap = map
        map.cameraPosition = CameraPosition.Builder()
            .target(SHEVCHENKIVSKYI_CENTER)
            .zoom(14.0)
            .tilt(50.0)
            .build()
    }

    // Стиль перезавантажується при зміні теми — не чіпає вже встановлену камеру.
    LaunchedEffect(isDarkTheme, maplibreMap) {
        val map = maplibreMap ?: return@LaunchedEffect
        map.setStyle(MapStyles.forTheme(isDarkTheme)) { loadedStyle ->
            loadedStyle.ensure3dBuildings()
            loadedStyle.registerMarkerImages(context)
            mapStyle = loadedStyle
        }
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

    LaunchedEffect(placesSymbolManager, uiState.places) {
        val manager = placesSymbolManager ?: return@LaunchedEffect
        manager.deleteAll()
        val options = uiState.places.map { place ->
            SymbolOptions()
                .withLatLng(LatLng(place.lat, place.lng))
                .withIconImage(placeImageId(place.category, place.status))
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Мапа Інклюзивності") },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Пошук")
                    }
                    IconButton(onClick = onOpenFilters) {
                        Icon(Icons.Filled.FilterList, contentDescription = "Фільтри")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Додати перевірку") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    val selected = uiState.selectedPlace
                    if (selected != null) {
                        onOpenPlace(selected.id)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

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

            if (uiState.selectedPlace == null && uiState.selectedSos == null) {
                SosHelpButton(
                    onClick = onOpenSos,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                )
            }

            uiState.selectedPlace?.let { place ->
                PlaceCard(
                    place = place,
                    onClick = { onOpenPlace(place.id) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            uiState.selectedSos?.let { sos ->
                SosInfoCard(
                    sos = sos,
                    isVolunteer = isVolunteer,
                    onResolve = viewModel::resolveSelectedSos,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}
