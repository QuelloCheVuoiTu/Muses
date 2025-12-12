package it.unisannio.muses.utils

import com.mapbox.geojson.Point
import kotlin.math.pow

object PolylineUtils {
    
    /**
     * Decodes a polyline6 string into a list of coordinate points
     */
    fun decodePolyline6(polyline: String): List<Point> {
        val coordinates = mutableListOf<Point>()
        var index = 0
        var lat = 0
        var lng = 0
        
        while (index < polyline.length) {
            var shift = 0
            var result = 0
            var byte: Int
            
            do {
                byte = polyline[index++].code - 63
                result = result or ((byte and 0x1F) shl shift)
                shift += 5
            } while (byte >= 0x20)
            
            val deltaLat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += deltaLat
            
            shift = 0
            result = 0
            
            do {
                byte = polyline[index++].code - 63
                result = result or ((byte and 0x1F) shl shift)
                shift += 5
            } while (byte >= 0x20)
            
            val deltaLng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += deltaLng
            
            // Polyline6 uses precision 6 (10^6)
            val latDouble = lat.toDouble() / 10.0.pow(6.0)
            val lngDouble = lng.toDouble() / 10.0.pow(6.0)
            
            coordinates.add(Point.fromLngLat(lngDouble, latDouble))
        }
        
        return coordinates
    }
}