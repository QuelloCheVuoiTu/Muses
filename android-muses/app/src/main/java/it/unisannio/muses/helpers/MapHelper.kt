package it.unisannio.muses.helpers

import android.content.Context
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import android.view.ViewGroup
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.FillExtrusionLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.plugin.gestures.gestures
import it.unisannio.muses.data.repositories.MuseumRepository
import it.unisannio.muses.data.models.Museum
import it.unisannio.muses.data.models.Vehicle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DEFAULT_LOCATION = Point.fromLngLat(14.7700, 41.1290) // Centro di Benevento
private const val DEFAULT_ZOOM = 16.0
private const val DEFAULT_PITCH = 45.0

/**
 * Callback interface for museum icon clicks
 */
interface OnMuseumClickListener {
    fun onMuseumClicked(museum: Museum)
}

/**
 * Callback interface for vehicle icon clicks
 */
interface OnVehicleClickListener {
    fun onVehicleClicked(vehicle: Vehicle)
}

/**
 * Creates and configures a MapView instance.
 * The caller is responsible for attaching it into the view hierarchy and for lifecycle calls.
 */
fun setupMap(context: Context, mapView: MapView, onMuseumClickListener: OnMuseumClickListener? = null, onVehicleClickListener: OnVehicleClickListener? = null) {
    // Load satellite-streets style and set an angled camera
    mapView.mapboxMap.loadStyleUri(Style.SATELLITE_STREETS) { style ->
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(DEFAULT_LOCATION)
                .zoom(DEFAULT_ZOOM)
                .pitch(DEFAULT_PITCH)
                .build()
        )

        // Add buildings layer
        val buildingsLayer = FillExtrusionLayer("3d-buildings", "composite").apply {
            sourceLayer("building")
            filter(Expression.eq(Expression.get("extrude"), Expression.literal("true")))
            minZoom(15.0)
            fillExtrusionColor(Expression.rgb(170.0, 170.0, 160.0))
            fillExtrusionHeight(Expression.get("height"))
            fillExtrusionBase(Expression.get("min_height"))
            fillExtrusionOpacity(0.8)   // Completely opaque
        }

        style.addLayer(buildingsLayer)

        // Add layer to show user location and center camera on the user when available
        addUserLocationLayer(mapView)

        // Add museums layer (icons for each museum)
        addMuseumsLayer(mapView, onMuseumClickListener, onVehicleClickListener)

        // Add bicycle layer
        addBicycleLayer(mapView)

        // Add bicycle layer (icons for bicycle points)
        addBicycleLayer(mapView)
        addEBicycleLayer(mapView)
        addScooterLayer(mapView)
    }
}

/**
 * Fetches museums from the API and adds a SymbolLayer with `ic_museum` icons at their locations.
 * This uses the app's `MuseumRepository` suspend function and runs it on IO dispatcher.
 */
private fun addMuseumsLayer(mapView: MapView, onMuseumClickListener: OnMuseumClickListener?, onVehicleClickListener: OnVehicleClickListener?) {
    try {
        val context = mapView.context
        // Launch a coroutine on Main to call suspend repository and then update the style on the main thread
        CoroutineScope(Dispatchers.Main).launch {
            val museums: List<Museum>? = try {
                withContext(Dispatchers.IO) {
                    val repo = MuseumRepository()
                    val response = repo.getAll()
                    // Log response code for diagnostics
                    android.util.Log.d("MapHelper", "Museum API response code: ${response.code()}")
                    if (response.isSuccessful) response.body() else null
                }
            } catch (e: Exception) {
                // network or parse error
                android.util.Log.w("MapHelper", "Failed to fetch museums", e)
                null
            }

            if (museums.isNullOrEmpty()) {
                // nothing to add
                android.util.Log.d("MapHelper", "No museums returned from API")
                return@launch
            }

            try {
                mapView.getMapboxMap().getStyle { style ->
                    // Prepare features from museums
                    val features = museums.mapNotNull { museum ->
                        try {
                            val lat = museum.location.latitude
                            val lon = museum.location.longitude
                            Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
                                addStringProperty("id", museum.id)
                                addStringProperty("name", museum.name)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                    android.util.Log.d("MapHelper", "Prepared features for ${features.size} museums")

                    val featureCollection = FeatureCollection.fromFeatures(features)

                    // Add a GeoJsonSource
                    val sourceId = "museums-data-source"
                    val layerId = "museums-symbol-layer"

                    // Remove existing source/layer if present (safe re-run)
                    try { style.removeStyleLayer(layerId) } catch (_: Exception) {}
                    try { style.removeStyleSource(sourceId) } catch (_: Exception) {}

                    val source: GeoJsonSource = geoJsonSource(sourceId) {
                        // use data with the FeatureCollection JSON
                        data(featureCollection.toJson())
                    }
                    style.addSource(source)

                    // Prepare bitmap from drawable `ic_museum`
                    val drawable = ContextCompat.getDrawable(context, it.unisannio.muses.R.drawable.ic_museum)
                    android.util.Log.d("MapHelper", "Drawable ic_museum is null: ${drawable == null}")
                    if (drawable != null) {
                        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        // add image to style
                        try { style.addImage("ic-museum-image", bitmap); android.util.Log.d("MapHelper", "Added ic-museum-image to style") } catch (e: Exception) { android.util.Log.w("MapHelper","Failed to add image to style", e) }
                    }

                    // Add a SymbolLayer to show the icon
                    val symbolLayer = SymbolLayer(layerId, sourceId).apply {
                        iconImage("ic-museum-image")
                        iconAllowOverlap(true)
                        iconAnchor(IconAnchor.BOTTOM)
                        iconSize(1.0)
                    }

                    style.addLayer(symbolLayer)
                    android.util.Log.d("MapHelper", "Added museums symbol layer with id=$layerId and source=$sourceId")
                    
                    // Add click listener for museum icons if callback is provided
                    if (onMuseumClickListener != null) {
                        mapView.gestures.addOnMapClickListener { point ->
                            try {
                                // Find the museum closest to the clicked point (within a reasonable distance)
                                val clickThresholdDegrees = 0.001 // Roughly 100 meters
                                
                                val clickedMuseum = museums.minByOrNull { museum ->
                                    val museumPoint = Point.fromLngLat(museum.location.longitude, museum.location.latitude)
                                    // Calculate simple distance
                                    val dx = museumPoint.longitude() - point.longitude()
                                    val dy = museumPoint.latitude() - point.latitude()
                                    dx * dx + dy * dy
                                }
                                
                                // Check if the closest museum is within the click threshold
                                if (clickedMuseum != null) {
                                    val museumPoint = Point.fromLngLat(clickedMuseum.location.longitude, clickedMuseum.location.latitude)
                                    val dx = museumPoint.longitude() - point.longitude()
                                    val dy = museumPoint.latitude() - point.latitude()
                                    val distance = dx * dx + dy * dy
                                    
                                    if (distance <= clickThresholdDegrees * clickThresholdDegrees) {
                                        onMuseumClickListener.onMuseumClicked(clickedMuseum)
                                        return@addOnMapClickListener true // Consume the click event
                                    }
                                }
                                
                                // Check for vehicle clicks if no museum was clicked and onVehicleClickListener is provided
                                if (onVehicleClickListener != null) {
                                    val mockVehicles = createMockVehicles()
                                    val clickedVehicle = mockVehicles.minByOrNull { vehicle ->
                                        val vehiclePoint = Point.fromLngLat(vehicle.location.longitude, vehicle.location.latitude)
                                        val dx = vehiclePoint.longitude() - point.longitude()
                                        val dy = vehiclePoint.latitude() - point.latitude()
                                        dx * dx + dy * dy
                                    }
                                    
                                    if (clickedVehicle != null) {
                                        val vehiclePoint = Point.fromLngLat(clickedVehicle.location.longitude, clickedVehicle.location.latitude)
                                        val dx = vehiclePoint.longitude() - point.longitude()
                                        val dy = vehiclePoint.latitude() - point.latitude()
                                        val distance = dx * dx + dy * dy
                                        
                                        if (distance <= clickThresholdDegrees * clickThresholdDegrees) {
                                            onVehicleClickListener.onVehicleClicked(clickedVehicle)
                                            return@addOnMapClickListener true // Consume the click event
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("MapHelper", "Error handling map click", e)
                            }
                            false // Return false to allow other click listeners to handle the event
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MapHelper", "Failed to add museums layer", e)
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("MapHelper", "Could not schedule museums layer addition", e)
    }
}

private fun addUserLocationLayer(mapView: MapView) {
    try {
        // Use the Mapbox Maps SDK location component extension
        val locationComponent = mapView.location

        // Enable the location component and use the SDK default puck
        locationComponent.updateSettings {
            enabled = true
            // Use default location puck provided by the SDK
            // Show accuracy ring and pulsing by default when available
            showAccuracyRing = true
            pulsingEnabled = true
        }

        // Try to center the map camera on the user's last known location using FusedLocationProvider
        val context = mapView.context
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    try {
                        val userPoint = Point.fromLngLat(location.longitude, location.latitude)
                        mapView.mapboxMap.setCamera(
                            CameraOptions.Builder()
                                .center(userPoint)
                                .zoom(DEFAULT_ZOOM)
                                .pitch(DEFAULT_PITCH)
                                .build()
                        )
                    } catch (e: Exception) {
                        Log.w("MapHelper", "Could not center camera on user location", e)
                    }
                } else {
                    Log.d("MapHelper", "FusedLocationProvider returned null lastLocation")
                }
            }.addOnFailureListener { e ->
                Log.w("MapHelper", "Failed to get last location", e)
            }
        } else {
            Log.d("MapHelper", "Location permission not granted; skipping centering map on user")
        }
    } catch (e: Exception) {
        // Swallow exceptions in helper and log so map initialization doesn't fail if location component missing
        Log.w("MapHelper", "Could not add user location layer", e)
    }
}

private fun addBicycleLayer(mapView: MapView) {
    try {
        val context = mapView.context
        mapView.getMapboxMap().getStyle { style ->
            val bicyclePoints = listOf(
                Point.fromLngLat(14.7782, 41.1294), // Benevento - Corso Garibaldi (strada pedonale)
                Point.fromLngLat(14.7768, 41.1288), // Benevento - Via Port'Arsa (via del centro)
                Point.fromLngLat(14.7790, 41.1300)  // Benevento - Piazza Traiano (piazza aperta)
            )

            val features = bicyclePoints.map { Feature.fromGeometry(it) }
            val featureCollection = FeatureCollection.fromFeatures(features)

            val sourceId = "bicycle-data-source"
            val layerId = "bicycle-symbol-layer"

            try { style.removeStyleLayer(layerId) } catch (_: Exception) {}
            try { style.removeStyleSource(sourceId) } catch (_: Exception) {}

            val source: GeoJsonSource = geoJsonSource(sourceId) {
                data(featureCollection.toJson())
            }
            style.addSource(source)

            val drawable = ContextCompat.getDrawable(context, it.unisannio.muses.R.drawable.bicycle)
            if (drawable != null) {
                val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                try { style.addImage("ic-bicycle-image", bitmap) } catch (e: Exception) { android.util.Log.w("MapHelper","Failed to add image to style", e) }
            }

            val symbolLayer = SymbolLayer(layerId, sourceId).apply {
                iconImage("ic-bicycle-image")
                iconAllowOverlap(true)
                iconAnchor(IconAnchor.BOTTOM)
                iconSize(0.75)
            }

            style.addLayer(symbolLayer)
        }
    } catch (e: Exception) {
        android.util.Log.w("MapHelper", "Could not add bicycle layer", e)
    }
}

private fun addEBicycleLayer(mapView: MapView) {
    try {
        val context = mapView.context
        mapView.getMapboxMap().getStyle { style ->
            val bicyclePoints = listOf(
                Point.fromLngLat(14.7778, 41.1292), // Benevento - Via Carlo Torre (strada)
                Point.fromLngLat(14.7784, 41.1296), // Benevento - Piazza Roma (centro piazza)
                Point.fromLngLat(14.7772, 41.1305)  // Benevento - Via San Bartolomeo (via pedonale)
            )

            val features = bicyclePoints.map { Feature.fromGeometry(it) }
            val featureCollection = FeatureCollection.fromFeatures(features)
            // Use distinct IDs for e-bicycles so they don't conflict with regular bicycles
            val sourceId = "ebicycle-data-source"
            val layerId = "ebicycle-symbol-layer"

            try { style.removeStyleLayer(layerId) } catch (_: Exception) {}
            try { style.removeStyleSource(sourceId) } catch (_: Exception) {}

            val source: GeoJsonSource = geoJsonSource(sourceId) {
                data(featureCollection.toJson())
            }
            style.addSource(source)

            // drawable resource name: bicycle_e.xml -> R.drawable.bicycle_e
            val drawable = ContextCompat.getDrawable(context, it.unisannio.muses.R.drawable.bicycle_e)
            if (drawable != null) {
                val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                try { style.addImage("ic-ebicycle-image", bitmap) } catch (e: Exception) { android.util.Log.w("MapHelper","Failed to add image to style", e) }
            } else {
                android.util.Log.w("MapHelper", "Drawable bicycle_e is null")
            }

            val symbolLayer = SymbolLayer(layerId, sourceId).apply {
                iconImage("ic-ebicycle-image")
                iconAllowOverlap(true)
                iconAnchor(IconAnchor.BOTTOM)
                iconSize(0.75)
            }

            style.addLayer(symbolLayer)
        }
    } catch (e: Exception) {
        android.util.Log.w("MapHelper", "Could not add bicycle layer", e)
    }
}

private fun addScooterLayer(mapView: MapView) {
    try {
        val context = mapView.context
        mapView.getMapboxMap().getStyle { style ->
            val bicyclePoints = listOf(
                Point.fromLngLat(14.7786, 41.1293), // Benevento - Via dei Mulini (strada)
                Point.fromLngLat(14.7793, 41.1303), // Benevento - Largo Traiano (piazza davanti all'arco)
                Point.fromLngLat(14.7775, 41.1298)  // Benevento - Via Annunziata (via del centro)
            )

            val features = bicyclePoints.map { Feature.fromGeometry(it) }
            val featureCollection = FeatureCollection.fromFeatures(features)
            // Use distinct IDs for scooters so they don't conflict with other layers
            val sourceId = "scooter-data-source"
            val layerId = "scooter-symbol-layer"

            try { style.removeStyleLayer(layerId) } catch (_: Exception) {}
            try { style.removeStyleSource(sourceId) } catch (_: Exception) {}

            val source: GeoJsonSource = geoJsonSource(sourceId) {
                data(featureCollection.toJson())
            }
            style.addSource(source)

            // drawable resource name: scooter.xml -> R.drawable.scooter
            val drawable = ContextCompat.getDrawable(context, it.unisannio.muses.R.drawable.scooter)
            if (drawable != null) {
                val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                try { style.addImage("ic-scooter-image", bitmap) } catch (e: Exception) { android.util.Log.w("MapHelper","Failed to add image to style", e) }
            } else {
                android.util.Log.w("MapHelper", "Drawable scooter is null")
            }

            val symbolLayer = SymbolLayer(layerId, sourceId).apply {
                iconImage("ic-scooter-image")
                iconAllowOverlap(true)
                iconAnchor(IconAnchor.BOTTOM)
                iconSize(0.75)
            }

            style.addLayer(symbolLayer)
        }
    } catch (e: Exception) {
        android.util.Log.w("MapHelper", "Could not add bicycle layer", e)
    }
}

/**
 * Creates mock vehicle data for testing vehicle click functionality
 */
private fun createMockVehicles(): List<Vehicle> {
    val vehicles = mutableListOf<Vehicle>()
    
    // Bicycles
    val bicyclePoints = listOf(
        Pair(Point.fromLngLat(14.7782, 41.1294), "Corso Garibaldi - Benevento"),
        Pair(Point.fromLngLat(14.7768, 41.1288), "Via Port'Arsa - Benevento"),
        Pair(Point.fromLngLat(14.7790, 41.1300), "Piazza Traiano - Benevento")
    )
    
    bicyclePoints.forEachIndexed { index, (point, address) ->
        vehicles.add(Vehicle(
            id = "bike_$index",
            type = "Bicicletta",
            pricePerHour = 2.50,
            location = it.unisannio.muses.data.models.LocationRequestBody("mock_user", point.latitude(), point.longitude()),
            address = address,
            isAvailable = true,
            batteryLevel = null
        ))
    }
    
    // E-bikes
    val eBikePoints = listOf(
        Pair(Point.fromLngLat(14.7778, 41.1292), "Via Carlo Torre - Benevento"),
        Pair(Point.fromLngLat(14.7784, 41.1296), "Piazza Roma - Benevento"),
        Pair(Point.fromLngLat(14.7772, 41.1305), "Via San Bartolomeo - Benevento")
    )
    
    eBikePoints.forEachIndexed { index, (point, address) ->
        vehicles.add(Vehicle(
            id = "ebike_$index",
            type = "E-bike",
            pricePerHour = 4.00,
            location = it.unisannio.muses.data.models.LocationRequestBody("mock_user", point.latitude(), point.longitude()),
            address = address,
            isAvailable = true,
            batteryLevel = (70..95).random()
        ))
    }
    
    // Scooters
    val scooterPoints = listOf(
        Pair(Point.fromLngLat(14.7786, 41.1293), "Via dei Mulini - Benevento"),
        Pair(Point.fromLngLat(14.7793, 41.1303), "Largo Traiano - Benevento"),
        Pair(Point.fromLngLat(14.7775, 41.1298), "Via Annunziata - Benevento")
    )
    
    scooterPoints.forEachIndexed { index, (point, address) ->
        vehicles.add(Vehicle(
            id = "scooter_$index",
            type = "Monopattino",
            pricePerHour = 3.50,
            location = it.unisannio.muses.data.models.LocationRequestBody("mock_user", point.latitude(), point.longitude()),
            address = address,
            isAvailable = true,
            batteryLevel = (60..90).random()
        ))
    }
    
    return vehicles
}
