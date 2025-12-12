package it.unisannio.muses.data.repositories

import android.util.Base64
import android.util.Log
import it.unisannio.muses.api.ApiService
import it.unisannio.muses.data.models.LoginBody
import it.unisannio.muses.data.models.TokenResponse
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
            val rbacNameHeader = "USER" // Based on the cURL, assuming this is a fixed value

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

    suspend fun register(loginBody: LoginBody): Result<LoginResult> = withContext(Dispatchers.IO) {
        try {
            // Call the API to register the user
            val registerResponse: Response<Unit> = apiService.register(loginBody)

            if (registerResponse.isSuccessful) {
                Log.d("AuthRepository", "Register successful with code ${registerResponse.code()}, proceeding to login.")
                // If registration succeeded (HTTP 200), call login and return its result
                return@withContext login(loginBody)
            } else {
                val errorMessage = registerResponse.errorBody()?.string() ?: "Unknown registration error"
                Log.e("AuthRepository", "Registration failed: $errorMessage")
                Result.failure(RuntimeException(errorMessage))
            }
        } catch (e: HttpException) {
            Log.e("AuthRepository", "HTTP error during registration: ${e.message()}", e)
            Result.failure(e)
        } catch (e: IOException) {
            Log.e("AuthRepository", "Network error during registration: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Unexpected error during registration: ${e.message}", e)
            Result.failure(e)
        }
    }
}