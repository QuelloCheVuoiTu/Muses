package com.example.museo_gui

import android.util.Log
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.example.museo_gui.models.*
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.animation.flyTo

class MapManager(private val mapboxMap: MapboxMap) {

    companion object {
        private const val USER_LOCATION_SOURCE_ID = "user-location-source"
        private const val MUSEUM_DATA_SOURCE_ID = "museum-data-source"
        private const val DEFAULT_ZOOM_LEVEL = 15.0
    }

    fun updateUserLocationOnMap(location: Point) {
        try {
            mapboxMap.getStyle { style ->
                val source = style.getSourceAs<GeoJsonSource>(USER_LOCATION_SOURCE_ID)
                if (source != null) {
                    val userLocationFeature = Feature.fromGeometry(location)
                    val featureCollection = FeatureCollection.fromFeatures(listOf(userLocationFeature))

                    source.data(featureCollection.toJson())

                    Log.d("UserLocation", "Posizione utente aggiornata: ${location.latitude()}, ${location.longitude()}")
                } else {
                    Log.w("UserLocation", "Source utente non trovata")
                }
            }
        } catch (e: Exception) {
            Log.e("UserLocation", "Errore nell'aggiornamento posizione utente", e)
        }
    }

    fun flyToLocation(location: Point, zoom: Double = DEFAULT_ZOOM_LEVEL) {
        mapboxMap.flyTo(
            CameraOptions.Builder()
                .center(location)
                .zoom(zoom)
                .build()
        )
    }

    fun updateMapWithMuseums(museums: List<MuseumResponse>) {
        mapboxMap.getStyle { style ->
            // Rimuovi i layer esistenti se presenti
            try {
                if (style.styleLayerExists("museum-layer")) {
                    style.removeStyleLayer("museum-layer")
                }
                if (style.styleLayerExists("minor-museum-layer")) {
                    style.removeStyleLayer("minor-museum-layer")
                }
                if (style.styleSourceExists(MUSEUM_DATA_SOURCE_ID)) {
                    style.removeStyleSource(MUSEUM_DATA_SOURCE_ID)
                }
            } catch (e: Exception) {
                Log.d("MapUpdate", "Layer non esistenti, procedo con l'aggiunta")
            }

            // Aggiungi nuovamente i dati e i layer
            // Qui dovresti chiamare le tue funzioni addInvisibleMuseumData e addMuseumLayers
            // che dovrebbero essere spostate in questa classe o in una classe helper

            Log.d("MapUpdate", "Mappa aggiornata con ${museums.size} musei")
        }
    }

    // Sposta qui le funzioni addInvisibleMuseumData e addMuseumLayers dalla MainActivity
    // private fun addInvisibleMuseumData(style: Style) { ... }
    // private fun addMuseumLayers(style: Style) { ... }
}