"""Межа Шевченківського району: той самий GeoJSON Nominatim (OSM relation 1755014), що й у district_boundary."""
import json


def load_district(path):
    data = json.load(open(path, encoding='utf-8'))
    item = data[0] if isinstance(data, list) else data
    geo = item['geojson']
    polygons = [geo['coordinates']] if geo['type'] == 'Polygon' else geo['coordinates']
    rings = [poly[0] for poly in polygons]  # зовнішні контури (lon, lat)
    lats = [pt[1] for ring in rings for pt in ring]
    lons = [pt[0] for ring in rings for pt in ring]
    # Наближений прямокутник для швидкого відсіву: (minLat, minLon, maxLat, maxLon).
    return rings, (min(lats), min(lons), max(lats), max(lons))


def inside_district(lat, lon, rings):
    """Ray casting по зовнішніх контурах (дзеркало isInsideAnyRing у застосунку)."""
    for ring in rings:
        inside = False
        j = len(ring) - 1
        for i in range(len(ring)):
            xi, yi = ring[i]
            xj, yj = ring[j]
            if (yi > lat) != (yj > lat) and lon < (xj - xi) * (lat - yi) / (yj - yi) + xi:
                inside = not inside
            j = i
        if inside:
            return True
    return False
