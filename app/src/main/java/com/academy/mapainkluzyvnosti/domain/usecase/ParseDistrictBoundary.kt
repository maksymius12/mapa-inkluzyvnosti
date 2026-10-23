package com.academy.mapainkluzyvnosti.domain.usecase

import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Дістає лише зовнішні контури (без отворів) з GeoJSON Polygon/MultiPolygon, як повертає Nominatim. */
fun parseOuterRings(geojson: JsonObject): List<List<GeoPoint>> {
    val type = geojson["type"]?.jsonPrimitive?.content ?: return emptyList()
    val coordinates = geojson["coordinates"]?.jsonArray ?: return emptyList()
    return when (type) {
        "Polygon" -> listOfNotNull(coordinates.firstOrNull()?.jsonArray?.toRing())
        "MultiPolygon" -> coordinates.mapNotNull { polygon -> polygon.jsonArray.firstOrNull()?.jsonArray?.toRing() }
        else -> emptyList()
    }
}

private fun JsonArray.toRing(): List<GeoPoint> = map { coordinate ->
    val pair = coordinate.jsonArray
    GeoPoint(lat = pair[1].jsonPrimitive.double, lng = pair[0].jsonPrimitive.double)
}

/** Ray casting: чи точка всередині хоча б одного з зовнішніх контурів полігону/мультиполігону. */
fun isInsideAnyRing(point: GeoPoint, rings: List<List<GeoPoint>>): Boolean =
    rings.any { ring -> pointInRing(point, ring) }

private fun pointInRing(point: GeoPoint, ring: List<GeoPoint>): Boolean {
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val pi = ring[i]
        val pj = ring[j]
        val intersects = (pi.lat > point.lat) != (pj.lat > point.lat) &&
            point.lng < (pj.lng - pi.lng) * (point.lat - pi.lat) / (pj.lat - pi.lat) + pi.lng
        if (intersects) inside = !inside
        j = i
    }
    return inside
}
