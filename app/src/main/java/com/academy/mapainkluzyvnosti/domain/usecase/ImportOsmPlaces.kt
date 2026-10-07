package com.academy.mapainkluzyvnosti.domain.usecase

import com.academy.mapainkluzyvnosti.data.model.AccessStatus
import com.academy.mapainkluzyvnosti.data.model.GeoPoint
import com.academy.mapainkluzyvnosti.data.model.PlaceCategory
import com.academy.mapainkluzyvnosti.data.remote.NominatimApi
import com.academy.mapainkluzyvnosti.data.remote.OverpassApi
import com.academy.mapainkluzyvnosti.data.remote.OverpassElement
import com.academy.mapainkluzyvnosti.data.remote.dto.PlaceWriteDto
import com.academy.mapainkluzyvnosti.data.remote.dto.wktPoint
import com.academy.mapainkluzyvnosti.data.repository.PlaceRepository

private data class BoundingBox(val minLat: Double, val minLng: Double, val maxLat: Double, val maxLng: Double)

/** OSM-теги, які цікавлять мапу, і їхнє відображення на [PlaceCategory]. */
private val OSM_TAG_CATEGORIES: List<Triple<String, String, PlaceCategory>> = listOf(
    Triple("amenity", "school", PlaceCategory.EDUCATION),
    Triple("amenity", "kindergarten", PlaceCategory.EDUCATION),
    Triple("amenity", "hospital", PlaceCategory.HEALTH),
    Triple("amenity", "clinic", PlaceCategory.HEALTH),
    Triple("amenity", "doctors", PlaceCategory.HEALTH),
    Triple("amenity", "pharmacy", PlaceCategory.HEALTH),
    Triple("amenity", "townhall", PlaceCategory.ADMIN),
    Triple("office", "government", PlaceCategory.ADMIN),
    Triple("shop", "supermarket", PlaceCategory.SHOP),
    Triple("railway", "station", PlaceCategory.TRANSIT),
    Triple("railway", "subway_entrance", PlaceCategory.TRANSIT)
)

class ImportOsmPlaces(
    private val nominatimApi: NominatimApi,
    private val overpassApi: OverpassApi,
    private val placeRepository: PlaceRepository
) {

    suspend operator fun invoke(districtQuery: String = "Шевченківський район, Київ, Україна") {
        val district = nominatimApi.searchDistrictPolygon(districtQuery) ?: return
        val geojson = district.geojson ?: return
        val rings = parseOuterRings(geojson)
        if (rings.isEmpty()) return

        val bbox = computeBoundingBox(rings)
        val ql = buildOverpassQuery(bbox)
        val elements = overpassApi.query(ql)

        val places = elements.mapNotNull { element ->
            val lat = element.resolvedLat ?: return@mapNotNull null
            val lng = element.resolvedLng ?: return@mapNotNull null
            if (!isInsideAnyRing(GeoPoint(lat, lng), rings)) return@mapNotNull null
            element.toPlaceWriteDto(lat, lng)
        }

        placeRepository.importOsmPlaces(places)
    }

    private fun OverpassElement.toPlaceWriteDto(lat: Double, lng: Double): PlaceWriteDto? {
        val (key, value, category) = OSM_TAG_CATEGORIES.firstOrNull { (key, value, _) -> tags[key] == value }
            ?: return null
        val name = tags["name"] ?: value.replaceFirstChar { it.uppercase() }
        val address = listOfNotNull(tags["addr:street"], tags["addr:housenumber"]).joinToString(" ").ifBlank { "" }
        val status = when (tags["wheelchair"]) {
            "yes" -> AccessStatus.ACCESSIBLE
            "limited" -> AccessStatus.PARTIAL
            "no" -> AccessStatus.BARRIER
            else -> AccessStatus.UNVERIFIED
        }
        return PlaceWriteDto(
            id = "osm-$type-$id",
            name = name,
            address = address,
            category = category,
            location = wktPoint(lat, lng),
            status = status,
            source = "osm"
        )
    }

    private fun computeBoundingBox(rings: List<List<GeoPoint>>): BoundingBox {
        val allPoints = rings.flatten()
        return BoundingBox(
            minLat = allPoints.minOf { it.lat },
            minLng = allPoints.minOf { it.lng },
            maxLat = allPoints.maxOf { it.lat },
            maxLng = allPoints.maxOf { it.lng }
        )
    }

    private fun buildOverpassQuery(bbox: BoundingBox): String {
        val bboxStr = "${bbox.minLat},${bbox.minLng},${bbox.maxLat},${bbox.maxLng}"
        val filters = OSM_TAG_CATEGORIES.joinToString("\n") { (key, value, _) ->
            "  node[\"$key\"=\"$value\"]($bboxStr);\n  way[\"$key\"=\"$value\"]($bboxStr);\n  relation[\"$key\"=\"$value\"]($bboxStr);"
        }
        return "[out:json][timeout:60];\n(\n$filters\n);\nout center;"
    }

    /** Ray casting: чи точка всередині хоча б одного з зовнішніх контурів полігону/мультиполігону. */
    private fun isInsideAnyRing(point: GeoPoint, rings: List<List<GeoPoint>>): Boolean =
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
}
