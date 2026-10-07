package com.academy.mapainkluzyvnosti.domain.usecase

import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import com.academy.mapainkluzyvnosti.data.model.Route
import com.academy.mapainkluzyvnosti.data.remote.OsrmApi
import java.util.UUID

class BuildRoute(private val osrmApi: OsrmApi) {

    suspend operator fun invoke(from: GeoPoint, fromName: String, to: GeoPoint, toName: String): Route? {
        val osrmRoute = osrmApi.footRoute(from.lat, from.lng, to.lat, to.lng) ?: return null
        val geometry = osrmRoute.geometry.coordinates.map { (lng, lat) -> GeoPoint(lat = lat, lng = lng) }
        return Route(
            id = UUID.randomUUID().toString(),
            fromName = fromName,
            toName = toName,
            from = from,
            to = to,
            geometry = geometry,
            distanceMeters = osrmRoute.distance,
            durationSeconds = osrmRoute.duration
        )
    }
}
