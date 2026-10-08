package com.academy.mapainkluzyvnosti.domain.usecase

import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import com.academy.mapainkluzyvnosti.data.model.Place
import com.academy.mapainkluzyvnosti.data.model.Route
import kotlin.math.cos
import kotlin.math.sqrt

/** Знаходить місця зі статусом [AccessStatus.BARRIER], що потрапляють у буфер навколо маршруту. */
object FindBarriersOnRoute {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    operator fun invoke(route: Route, places: List<Place>, bufferMeters: Double = 30.0): List<String> {
        if (route.geometry.size < 2) return emptyList()
        return places
            .asSequence()
            .filter { it.status == AccessStatus.BARRIER }
            .filter { place -> distanceToRoute(GeoPoint(place.lat, place.lng), route.geometry) <= bufferMeters }
            .map { it.id }
            .toList()
    }

    private fun distanceToRoute(point: GeoPoint, geometry: List<GeoPoint>): Double =
        (0 until geometry.size - 1).minOf { i -> distanceToSegment(point, geometry[i], geometry[i + 1]) }

    private fun distanceToSegment(point: GeoPoint, segStart: GeoPoint, segEnd: GeoPoint): Double {
        // Проєкція в локальні метричні координати (рівнорівнинне наближення, достатнє для міського масштабу).
        val x0 = lngToMeters(point.lng, point.lat)
        val y0 = latToMeters(point.lat)
        val x1 = lngToMeters(segStart.lng, segStart.lat)
        val y1 = latToMeters(segStart.lat)
        val x2 = lngToMeters(segEnd.lng, segEnd.lat)
        val y2 = latToMeters(segEnd.lat)

        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else (((x0 - x1) * dx + (y0 - y1) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return sqrt((x0 - projX) * (x0 - projX) + (y0 - projY) * (y0 - projY))
    }

    private fun latToMeters(lat: Double): Double = Math.toRadians(lat) * EARTH_RADIUS_METERS

    private fun lngToMeters(lng: Double, atLat: Double): Double =
        Math.toRadians(lng) * EARTH_RADIUS_METERS * cos(Math.toRadians(atLat))

    /**
     * Ділянки маршруту навколо кожного бар'єра (до [spanMeters] в обидва боки від найближчої точки лінії) —
     * їх підсвічують червоним поверх лінії маршруту.
     */
    fun highlightSegments(route: Route, barriers: List<Place>, spanMeters: Double = 45.0): List<List<GeoPoint>> {
        val geometry = route.geometry
        if (geometry.size < 2) return emptyList()
        return barriers.mapNotNull { place ->
            val point = GeoPoint(place.lat, place.lng)
            val nearest = geometry.indices.minByOrNull { i -> distanceMeters(point, geometry[i]) } ?: return@mapNotNull null
            var from = nearest
            var covered = 0.0
            while (from > 0 && covered < spanMeters) {
                covered += distanceMeters(geometry[from], geometry[from - 1])
                from--
            }
            var to = nearest
            covered = 0.0
            while (to < geometry.lastIndex && covered < spanMeters) {
                covered += distanceMeters(geometry[to], geometry[to + 1])
                to++
            }
            geometry.subList(from, to + 1).takeIf { it.size >= 2 }
        }
    }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val dx = lngToMeters(a.lng - b.lng, a.lat)
        val dy = latToMeters(a.lat - b.lat)
        return sqrt(dx * dx + dy * dy)
    }
}
