package it.unisannio.muses.data.repositories

import android.util.Log
import it.unisannio.muses.api.ApiService
import it.unisannio.muses.data.models.LocationRequestBody
import retrofit2.HttpException
import java.io.IOException
import java.lang.Exception

class LocationRepository(private val apiService: ApiService) {

    suspend fun sendLocationToServer(userId: String, latitude: Double, longitude: Double): Result<Unit> {
        return try {
            apiService.sendLocation(LocationRequestBody(userId, latitude, longitude))
            Log.d("LocationRepository", "Location sent successfully: latitude=$latitude, longitude=$longitude")
            Result.success(Unit)
        } catch (e: HttpException) {
            // Handle HTTP errors (e.g., 404 Not Found, 500 Internal Server Error)
            Log.e("LocationRepository", "HTTP error sending location: ${e.message()}", e)
            Result.failure(e)
        } catch (e: IOException) {
            // Handle network connectivity issues
            Log.e("LocationRepository", "Network error sending location: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            // Handle any other generic errors
            Log.e("LocationRepository", "Unexpected error sending location: ${e.message}", e)
            Result.failure(e)
        }
    }
}