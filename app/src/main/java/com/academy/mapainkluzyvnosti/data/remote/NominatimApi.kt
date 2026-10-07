package com.academy.mapainkluzyvnosti.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class NominatimResult(
    val lat: String? = null,
    val lon: String? = null,
    val geojson: JsonObject? = null,
    val boundingbox: List<String> = emptyList()
)

data class GeocodedPoint(val lat: Double, val lng: Double)

class NominatimApi(private val client: HttpClient) {

    suspend fun searchDistrictPolygon(query: String): NominatimResult? {
        val results: List<NominatimResult> = search(query, includePolygon = true)
        return results.firstOrNull()
    }

    /** Пряме геокодування вільного тексту адреси в координати (для полів "звідки"/"куди"). */
    suspend fun searchPlace(query: String): GeocodedPoint? {
        val result = search(query, includePolygon = false).firstOrNull() ?: return null
        val lat = result.lat?.toDoubleOrNull() ?: return null
        val lng = result.lon?.toDoubleOrNull() ?: return null
        return GeocodedPoint(lat, lng)
    }

    private suspend fun search(query: String, includePolygon: Boolean): List<NominatimResult> =
        client.get("https://nominatim.openstreetmap.org/search") {
            header("User-Agent", "MapaInkluzyvnosti/1.0 (Android; accessibility-map)")
            parameter("format", "jsonv2")
            if (includePolygon) parameter("polygon_geojson", "1")
            parameter("limit", "1")
            parameter("q", query)
        }.body()
}
