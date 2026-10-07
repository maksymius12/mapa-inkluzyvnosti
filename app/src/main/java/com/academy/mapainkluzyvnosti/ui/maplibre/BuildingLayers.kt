package com.academy.mapainkluzyvnosti.ui.maplibre

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.PropertyFactory

private const val LIBERTY_BUILDINGS_3D_LAYER_ID = "building-3d"
private const val CUSTOM_BUILDINGS_3D_LAYER_ID = "mapa-buildings-3d"

/**
 * Стиль "liberty" від OpenFreeMap уже містить шар "building-3d" (fill-extrusion,
 * джерело "openmaptiles", source-layer "building"). Стиль "dark" — ні, тож для
 * паритету додаємо еквівалентний шар самі, і лише якщо стиль ще не має жодного зі
 * своїх (щоб не накласти два fill-extrusion шари один на інший).
 */
fun Style.ensure3dBuildings() {
    if (getLayer(LIBERTY_BUILDINGS_3D_LAYER_ID) != null || getLayer(CUSTOM_BUILDINGS_3D_LAYER_ID) != null) return

    val layer = FillExtrusionLayer(CUSTOM_BUILDINGS_3D_LAYER_ID, "openmaptiles")
        .withSourceLayer("building")
        .withProperties(
            PropertyFactory.fillExtrusionColor("hsl(225, 12%, 32%)"),
            PropertyFactory.fillExtrusionHeight(Expression.get("render_height")),
            PropertyFactory.fillExtrusionBase(Expression.get("render_min_height")),
            PropertyFactory.fillExtrusionOpacity(0.8f)
        )
    layer.setMinZoom(14f)
    addLayer(layer)
}
