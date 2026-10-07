package com.academy.mapainkluzyvnosti.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val lat: Double, val lng: Double)

@Serializable
data class Route(
    val id: String,
    val fromName: String,
    val toName: String,
    val from: GeoPoint,
    val to: GeoPoint,
    val geometry: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val barrierPlaceIds: List<String> = emptyList()
)
