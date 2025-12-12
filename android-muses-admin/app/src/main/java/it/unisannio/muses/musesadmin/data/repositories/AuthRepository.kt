package it.unisannio.muses.musesadmin.data.repositories

import android.util.Base64
import android.util.Log
import it.unisannio.muses.musesadmin.api.ApiService
import it.unisannio.muses.musesadmin.data.models.LoginBody
import it.unisannio.muses.musesadmin.data.models.TokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.nio.charset.StandardCharsets

class AuthRepository(private val apiService: ApiService) {

    // Simple container to return both the token response and the optional entityId header
    data class LoginResult(val tokenResponse: TokenResponse, val entityId: String?)

    suspend fun login(loginBody: LoginBody): Result<LoginResult> = withContext(Dispatchers.IO) {
        try {
            // Construct the Basic Auth string
            val credentials = "${loginBody.username}:${loginBody.password}"
            val base64Credentials = Base64.encodeToString(credentials.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            val authHeader = "Basic $base64Credentials"
            val rbacNameHeader = "ADMIN" // For museum admin

            val response: Response<TokenResponse> = apiService.login(
                authorization = authHeader,
                rbacName = rbacNameHeader
            )

            if (response.isSuccessful && response.body() != null) {
                Log.d("AuthRepository", "Login successful, token received.")
                // Extract the entityid header if present
                val entityIdHeader = response.headers().get("entityid")
                Result.success(LoginResult(response.body()!!, entityIdHeader))
            } else {
                val errorMessage = response.errorBody()?.string() ?: "Unknown error"
                Log.e("AuthRepository", "Login failed: $errorMessage")
                Result.failure(RuntimeException(errorMessage))
            }
        } catch (e: HttpException) {
            Log.e("AuthRepository", "HTTP error during login: ${e.message()}", e)
            Result.failure(e)
        } catch (e: IOException) {
            Log.e("AuthRepository", "Network error during login: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Unexpected error during login: ${e.message}", e)
            Result.failure(e)
        }
    }
}