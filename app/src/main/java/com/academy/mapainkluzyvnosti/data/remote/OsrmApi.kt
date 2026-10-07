package com.academy.mapainkluzyvnosti.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

@Serializable
data class OsrmResponse(val routes: List<OsrmRoute>, val code: String)

@Serializable
data class OsrmRoute(
    val geometry: OsrmGeometry,
    val distance: Double,
    val duration: Double
)

@Serializable
data class OsrmGeometry(val coordinates: List<List<Double>>)

class OsrmApi(private val client: HttpClient) {

    /** Пішохідний маршрут між двома точками. Повертає null, якщо OSRM не знайшов шлях. */
    suspend fun footRoute(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): OsrmRoute? {
        val response: OsrmResponse = client.get(
            "https://router.project-osrm.org/route/v1/foot/$fromLng,$fromLat;$toLng,$toLat"
        ) {
            parameter("overview", "full")
            parameter("geometries", "geojson")
        }.body()
        return response.routes.firstOrNull()
    }
}
