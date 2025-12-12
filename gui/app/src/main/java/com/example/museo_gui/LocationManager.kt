package com.example.museo_gui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.mapbox.geojson.Point

class AppLocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private lateinit var locationListener: LocationListener
    private var currentLocation: Point? = null
    private var initialCameraSet = false

    interface LocationCallback {
        fun onLocationChanged(location: Point)
        fun onInitialLocationSet(location: Point)
    }

    private var callback: LocationCallback? = null

    fun setLocationCallback(callback: LocationCallback) {
        this.callback = callback
    }

    fun getCurrentLocation(): Point? = currentLocation

    fun isInitialCameraSet(): Boolean = initialCameraSet

    fun setInitialCameraSet(set: Boolean) {
        initialCameraSet = set
    }

    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun startLocationUpdates() {
        try {
            if (!hasLocationPermission()) {
                Log.w("Location", "Permessi di localizzazione non concessi")
                return
            }

            // Ottieni ultima posizione conosciuta
            val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            lastLocation?.let { location ->
                currentLocation = Point.fromLngLat(location.longitude, location.latitude)
                Log.d("Location", "Ultima posizione conosciuta: ${location.latitude}, ${location.longitude}")

                callback?.onLocationChanged(currentLocation!!)

                if (!initialCameraSet) {
                    callback?.onInitialLocationSet(currentLocation!!)
                    initialCameraSet = true
                }
            }

            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    currentLocation = Point.fromLngLat(location.longitude, location.latitude)
                    Log.d("Location", "Nuova posizione: ${location.latitude}, ${location.longitude}")

                    callback?.onLocationChanged(currentLocation!!)

                    if (!initialCameraSet) {
                        callback?.onInitialLocationSet(currentLocation!!)
                        initialCameraSet = true
                    }
                }

                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated in API level 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000, // Aggiorna ogni 2 secondi
                    1f,    // Minimo 1 metri di spostamento
                    locationListener
                )
            }
        } catch (e: Exception) {
            Log.e("Location", "Errore location", e)
        }
    }

    fun stopLocationUpdates() {
        if (::locationListener.isInitialized && hasLocationPermission()) {
            locationManager.removeUpdates(locationListener)
        }
    }

    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        val PARIS_DEFAULT_LOCATION = Point.fromLngLat(2.3522, 48.8566)
        const val DEFAULT_ZOOM_LEVEL = 15.0
    }
}