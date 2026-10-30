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
    /** Середній рейтинг і кількість відгуків із place_reviews — оновлює тригер (20261030130000_place_reviews.sql). */
    @SerialName("avg_rating") val rating: Double? = null,
    @SerialName("reviews_count") val reviewCount: Int = 0,
    @SerialName("has_accessible_parking") val hasAccessibleParking: Boolean = false,
    /** Автор користувацького місця (source = "user"); null — OSM або старі записи. */
    @SerialName("created_by") val createdBy: String? = null,
    /** Підпис джерела для імпортованих відкритих даних (source = "open_data"). */
    val attribution: String? = null
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

/**
 * Нове користувацьке місце. `id`, `status`, `created_at` ставить сервер (тригер places_before_insert),
 * він же перевіряє межі району, добовий ліміт і поля. Без значень за замовчуванням —
 * щоб усі поля гарантовано потрапили в JSON.
 */
@Serializable
data class UserPlaceInsertDto(
    val name: String,
    val address: String,
    val category: PlaceCategory,
    val location: String,
    val source: String,
    @SerialName("created_by") val createdBy: String
)

/**
 * Відповідь на INSERT нового місця — лише поля, які ставить сервер (id, статус тощо).
 * Координати клієнт уже знає, тож відповідь не залежить від generated-колонок lat/lng.
 */
@Serializable
data class CreatedPlaceDto(
    val id: String,
    val name: String,
    val address: String? = null,
    val category: PlaceCategory,
    val status: AccessStatus,
    val source: String,
    @SerialName("created_by") val createdBy: String? = null
)

@Serializable
data class PlaceReportInsertDto(
    @SerialName("place_id") val placeId: String,
    @SerialName("reporter_id") val reporterId: String,
    val reason: String,
    val comment: String?
)

@Serializable
data class PlaceReportReadDto(
    @SerialName("place_id") val placeId: String
)

/** Відповідь PATCH з `select=id` — щоб відрізнити «оновлено» від «0 рядків» (RLS/тригер/місця немає). */
@Serializable
data class CreatedPlaceIdDto(val id: String)
