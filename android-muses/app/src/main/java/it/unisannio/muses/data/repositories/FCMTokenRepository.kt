package it.unisannio.muses.data.repositories

import android.util.Log
import it.unisannio.muses.api.ApiService
import it.unisannio.muses.data.models.FCMTokenRequestBody
import retrofit2.HttpException
import java.io.IOException
import java.lang.Exception

class FCMTokenRepository(private val apiService: ApiService) {

    /**
     * Sends the FCM token to the server for a specific user.
     *
     * @param userId The ID of the user whose token is being sent.
     * @param token The FCM token string.
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun sendTokenToServer(userId: String, token: String): Result<Unit> {
        return try {
            apiService.sendToken(FCMTokenRequestBody(userId, token))
            Log.d("FCMTokenRepository", "Token sent successfully for user: $userId")
            Result.success(Unit)
        } catch (e: HttpException) {
            // Handle HTTP errors (e.g., 404 Not Found, 500 Internal Server Error)
            Log.e("FCMTokenRepository", "HTTP error sending token: ${e.message()}", e)
            Result.failure(e)
        } catch (e: IOException) {
            // Handle network connectivity issues
            Log.e("FCMTokenRepository", "Network error sending token: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            // Handle any other generic errors
            Log.e("FCMTokenRepository", "Unexpected error sending token: ${e.message}", e)
            Result.failure(e)
        }
    }
}