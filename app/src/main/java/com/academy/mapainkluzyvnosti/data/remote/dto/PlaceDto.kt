package com.academy.mapainkluzyvnosti.data.remote.dto

import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Читання з `places`: `lat`/`lng` — generated-колонки (ST_Y/ST_X від `location`),
 * тож `location` (geography) із результату вибірки просто ігнорується.
 */
@Serializable
data class PlaceReadDto(
    val id: String,
    val name: String,
    val address: String,
    val category: PlaceCategory,
    val lat: Double,
    val lng: Double,
    val status: AccessStatus,
    val source: String,
    @SerialName("verified_note") val verifiedNote: String,
    val rating: Double? = null,
    @SerialName("review_count") val reviewCount: Int = 0,
    @SerialName("has_accessible_parking") val hasAccessibleParking: Boolean = false
)

/**
 * Запис у `places`: `location` передається як WKT-текст ("POINT(lng lat)"),
 * Postgres неявно приводить його до geography. lat/lng — generated, у insert не йдуть.
 */
@Serializable
data class PlaceWriteDto(
    val id: String,
    val name: String,
    val address: String,
    val category: PlaceCategory,
    val location: String,
    val status: AccessStatus,
    val source: String,
    @SerialName("verified_note") val verifiedNote: String = ""
)

fun wktPoint(lat: Double, lng: Double): String = "POINT($lng $lat)"
