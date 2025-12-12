package it.unisannio.muses.musesadmin.data.repositories

import android.util.Log
import it.unisannio.muses.musesadmin.api.ApiService
import it.unisannio.muses.musesadmin.data.models.RewardResponse
import it.unisannio.muses.musesadmin.data.models.UseRewardResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class RewardRepository(private val apiService: ApiService) {

    suspend fun getRewardDetails(rewardId: String): Result<RewardResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getRewardDetails(rewardId)
            if (response.isSuccessful && response.body() != null) {
                Log.d("RewardRepository", "Successfully fetched reward details for ID: $rewardId")
                Result.success(response.body()!!)
            } else {
                val errorMessage = response.errorBody()?.string() ?: "Failed to fetch reward details"
                Log.e("RewardRepository", "Failed to fetch reward details: $errorMessage")
                Result.failure(RuntimeException(errorMessage))
            }
        } catch (e: HttpException) {
            Log.e("RewardRepository", "HTTP error while fetching reward details: ${e.message()}", e)
            Result.failure(e)
        } catch (e: IOException) {
            Log.e("RewardRepository", "Network error while fetching reward details: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("RewardRepository", "Unexpected error while fetching reward details: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun useReward(rewardId: String): Result<UseRewardResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.useReward(rewardId)
            if (response.isSuccessful && response.body() != null) {
                Log.d("RewardRepository", "Successfully used reward with ID: $rewardId")
                Result.success(response.body()!!)
            } else {
                val errorMessage = response.errorBody()?.string() ?: "Failed to use reward"
                Log.e("RewardRepository", "Failed to use reward: $errorMessage")
                Result.failure(RuntimeException(errorMessage))
            }
        } catch (e: HttpException) {
            Log.e("RewardRepository", "HTTP error while using reward: ${e.message()}", e)
            Result.failure(e)
        } catch (e: IOException) {
            Log.e("RewardRepository", "Network error while using reward: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("RewardRepository", "Unexpected error while using reward: ${e.message}", e)
            Result.failure(e)
        }
    }
}