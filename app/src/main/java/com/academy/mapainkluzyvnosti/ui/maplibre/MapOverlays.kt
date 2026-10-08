package com.academy.mapainkluzyvnosti.ui.maplibre

import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val DIM_SOURCE_ID = "mapa-district-dim-source"
private const val DIM_LAYER_ID = "mapa-district-dim-layer"
private const val DISTRICT_LABEL_SOURCE_ID = "mapa-district-label-source"
private const val DISTRICT_LABEL_LAYER_ID = "mapa-district-label-layer"
private const val USER_SOURCE_ID = "mapa-user-location-source"
private const val USER_HALO_LAYER_ID = "mapa-user-location-halo"
private const val USER_DOT_LAYER_ID = "mapa-user-location-dot"

const val DISTRICT_LABEL = "Шевченківський район"

private val UserBlue = android.graphics.Color.parseColor("#2F6BFF")

/**
 * Затемнення всього, що поза межами Шевченківського району (великий зовнішній прямокутник
 * з «діркою» по контуру району) + підпис району. Додавати до стилю до створення SymbolManager —
 * тоді мітки лежать над затемненням.
 */
fun Style.ensureDistrictLayers() {
    if (getSource(DIM_SOURCE_ID) == null) {
        addSource(GeoJsonSource(DIM_SOURCE_ID))
        addLayer(
            FillLayer(DIM_LAYER_ID, DIM_SOURCE_ID).withProperties(
                PropertyFactory.fillColor(android.graphics.Color.BLACK),
                PropertyFactory.fillOpacity(0.35f)
            )
        )
    }
    if (getSource(DISTRICT_LABEL_SOURCE_ID) == null) {
        addSource(GeoJsonSource(DISTRICT_LABEL_SOURCE_ID))
        addLayer(
            SymbolLayer(DISTRICT_LABEL_LAYER_ID, DISTRICT_LABEL_SOURCE_ID).withProperties(
                PropertyFactory.textField(DISTRICT_LABEL),
                PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                PropertyFactory.textSize(17f),
                PropertyFactory.textColor(android.graphics.Color.parseColor("#1B2437")),
                PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
                PropertyFactory.textHaloWidth(2f),
                PropertyFactory.textMaxWidth(7f),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true)
            )
        )
    }
}

fun Style.setDistrictBoundary(rings: List<List<GeoPoint>>) {
    val dimSource = getSourceAs<GeoJsonSource>(DIM_SOURCE_ID) ?: return
    val labelSource = getSourceAs<GeoJsonSource>(DISTRICT_LABEL_SOURCE_ID)
    val districtRing = rings.firstOrNull()?.map { Point.fromLngLat(it.lng, it.lat) }
    if (districtRing != null && districtRing.size >= 3) {
        val outerRing = listOf(
            Point.fromLngLat(-180.0, -85.0),
            Point.fromLngLat(-180.0, 85.0),
            Point.fromLngLat(180.0, 85.0),
            Point.fromLngLat(180.0, -85.0),
            Point.fromLngLat(-180.0, -85.0)
        )
        val closedDistrictRing = if (districtRing.first() == districtRing.last()) districtRing else districtRing + districtRing.first()
        dimSource.setGeoJson(Polygon.fromLngLats(listOf(outerRing, closedDistrictRing)))
        districtBounds(rings)?.center?.let { center ->
            labelSource?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(center.longitude, center.latitude)))
        }
    } else {
        dimSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        labelSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }
}

/** Прямокутник, що охоплює зовнішній контур району, — для обмеження камери. */
fun districtBounds(rings: List<List<GeoPoint>>): LatLngBounds? {
    val points = rings.firstOrNull()?.takeIf { it.size >= 3 } ?: return null
    return LatLngBounds.Builder().apply { points.forEach { include(LatLng(it.lat, it.lng)) } }.build()
}

/** Синя крапка поточного положення (з м'яким ореолом). */
fun Style.ensureUserLocationLayer() {
    if (getSource(USER_SOURCE_ID) != null) return
    addSource(GeoJsonSource(USER_SOURCE_ID))
    addLayer(
        CircleLayer(USER_HALO_LAYER_ID, USER_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(16f),
            PropertyFactory.circleColor(UserBlue),
            PropertyFactory.circleOpacity(0.22f),
            PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP)
        )
    )
    addLayer(
        CircleLayer(USER_DOT_LAYER_ID, USER_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(UserBlue),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(2.5f)
        )
    )
}

fun Style.setUserLocation(point: GeoPoint?) {
    val source = getSourceAs<GeoJsonSource>(USER_SOURCE_ID) ?: return
    if (point == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    } else {
        source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(point.lng, point.lat)))
    }
}
