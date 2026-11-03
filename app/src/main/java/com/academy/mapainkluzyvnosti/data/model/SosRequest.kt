package com.academy.mapainkluzyvnosti.data.model

import com.academy.mapainkluzyvnosti.data.local.LocationSource

data class SosRequest(
    val id: String,
    val userId: String?,
    /** null — локацію не вдалося визначити ([locationSource] = NONE). */
    val lat: Double?,
    val lng: Double?,
    /** null — людина ще не вказала, що сталося (запит надсилається одразу, тип — потім). */
    val problemType: SosProblemType?,
    val comment: String?,
    val status: SosStatus,
    val createdAt: String?,
    val locationSource: LocationSource = LocationSource.NONE,
    val locationAccuracyM: Float? = null,
    val locationAt: String? = null,
    val updatedAt: String? = null
) {
    val point: GeoPoint? get() = if (lat != null && lng != null) GeoPoint(lat, lng) else null
}
