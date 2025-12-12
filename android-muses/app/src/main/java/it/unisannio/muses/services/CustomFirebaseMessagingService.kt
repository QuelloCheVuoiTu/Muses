package it.unisannio.muses.services

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.data.repositories.LocationRepository
import it.unisannio.muses.data.repositories.FCMTokenRepository
import it.unisannio.muses.helpers.AuthTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.Exception

class CustomFirebaseMessagingService : FirebaseMessagingService() {

    // Retrieve the userId from shared preferences via AuthTokenManager.
    // Default to null if not available; callers should handle null gracefully.
    private val userId: String?
        get() = AuthTokenManager(applicationContext).getEntityId()

    // A CoroutineScope tied to the service's lifecycle for efficient resource management.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var tokenRepository: FCMTokenRepository
    private lateinit var locationRepository: LocationRepository


    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize the repositories
        tokenRepository = FCMTokenRepository(RetrofitInstance.api)
        locationRepository = LocationRepository(RetrofitInstance.api)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        if (remoteMessage.data.isNotEmpty()) {
            val type = remoteMessage.data["type"]
            if (type == "get_location") {
                sendLocation()
            }
        }
    }

    private fun sendLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permissions are not granted.
            // You might want to log this or handle it in some other way.
            Log.d("Location", "Location permissions not granted")
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    // Got the location. Now, send it to your server.
                    val latitude = location.latitude
                    val longitude = location.longitude

                    serviceScope.launch {
                        try {
                            val id = userId
                            if (id == null) {
                                Log.w("Location", "User id not available; skipping sending location")
                                return@launch
                            }
                            locationRepository.sendLocationToServer(id, latitude, longitude)
                            Log.d("Location", "Location sent successfully: latitude=$latitude, longitude=$longitude")
                        } catch (e: Exception) {
                            Log.e("Location", "Error sending location", e)
                        }
                    }
                } else {
                    Log.d("Location", "Location is null")
                }
            }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN_UPDATE", "Refreshed token: $token")

        // Send the new token to server.
        serviceScope.launch {
            try {
                val id = userId
                if (id == null) {
                    Log.w("FCM_TOKEN_UPDATE", "User id not available; skipping sending token")
                    return@launch
                }
                tokenRepository.sendTokenToServer(id, token)
            } catch (e: Exception) {
                Log.e("FCM_TOKEN_UPDATE", "Error sending token", e)
            }
        }
    }

    // Cancel the coroutine scope when the service is destroyed.
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch { }
    }
}
