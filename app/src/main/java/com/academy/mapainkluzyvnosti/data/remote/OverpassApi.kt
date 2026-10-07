package com.academy.mapainkluzyvnosti.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import kotlinx.serialization.Serializable

@Serializable
data class OverpassResponse(val elements: List<OverpassElement>)

@Serializable
data class OverpassCenter(val lat: Double, val lon: Double)

@Serializable
data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: Map<String, String> = emptyMap()
) {
    /** Для way/relation координати лежать у `center`, для node — напряму в lat/lon. */
    val resolvedLat: Double? get() = lat ?: center?.lat
    val resolvedLng: Double? get() = lon ?: center?.lon
}

class OverpassApi(private val client: HttpClient) {

    suspend fun query(overpassQl: String): List<OverpassElement> {
        val response: OverpassResponse = client.submitForm(
            url = "https://overpass-api.de/api/interpreter",
            formParameters = Parameters.build { append("data", overpassQl) }
        ).body()
        return response.elements
    }
}
