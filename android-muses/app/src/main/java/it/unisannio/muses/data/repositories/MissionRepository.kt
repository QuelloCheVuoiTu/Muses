package it.unisannio.muses.data.repositories

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.data.models.Mission
import okhttp3.ResponseBody
import retrofit2.Response

class MissionRepository {
    suspend fun generateMission(userId: String): Response<Unit> {
        return RetrofitInstance.api.generateMission(userId)
    }

    suspend fun getUserMissions(userId: String): Response<List<Mission>> {
        Log.d("MissionRepository", "Fetching missions for user: $userId")
        
        // Use raw response directly to avoid Gson parsing issues
        val rawResponse = RetrofitInstance.api.getUserMissionsRaw(userId)
        if (!rawResponse.isSuccessful) {
            Log.e("MissionRepository", "Raw response failed: ${rawResponse.code()} - ${rawResponse.message()}")
            return Response.success(emptyList())
        }

        val rawBody = rawResponse.body()?.string() ?: ""
        Log.d("MissionRepository", "Raw response body: $rawBody")
        
        if (rawBody.isEmpty()) {
            Log.w("MissionRepository", "Empty response body, returning empty list")
            return Response.success(emptyList())
        }
        
        return try {
            val jsonElement = JsonParser.parseString(rawBody)
            Log.d("MissionRepository", "JSON parsed successfully, isArray: ${jsonElement.isJsonArray}")
            
            if (jsonElement.isJsonArray) {
                val jsonArray = jsonElement.asJsonArray
                Log.d("MissionRepository", "JSON Array size: ${jsonArray.size()}")
                
                // Handle malformed response [missions_array, status_code]
                if (jsonArray.size() == 2 && jsonArray[1].isJsonPrimitive) {
                    Log.d("MissionRepository", "Detected malformed response format [array, status_code]")
                    val missionsArray = jsonArray[0]
                    if (missionsArray.isJsonArray) {
                        val missions = Gson().fromJson(missionsArray, Array<Mission>::class.java).toList()
                        Log.d("MissionRepository", "Successfully parsed ${missions.size} missions from malformed response")
                        Response.success(missions)
                    } else {
                        Log.w("MissionRepository", "First element is not an array in malformed response")
                        Response.success(emptyList())
                    }
                } else {
                    Log.d("MissionRepository", "Attempting to parse as normal mission array")
                    // Try to parse as normal array
                    val missions = Gson().fromJson(jsonArray, Array<Mission>::class.java).toList()
                    Log.d("MissionRepository", "Successfully parsed ${missions.size} missions from normal array")
                    Response.success(missions)
                }
            } else {
                Log.w("MissionRepository", "JSON element is not an array, it's: ${jsonElement.javaClass.simpleName}")
                Response.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e("MissionRepository", "JSON parsing failed for user: $userId", e)
            // Return empty list to prevent crashes
            Response.success(emptyList())
        }
    }

    suspend fun getUserMissionsRaw(userId: String): Response<ResponseBody> {
        return RetrofitInstance.api.getUserMissionsRaw(userId)
    }

    suspend fun startMission(missionId: String): Response<Unit> {
        Log.d("MissionRepository", "Starting mission with ID: $missionId")
        return try {
            val response = RetrofitInstance.api.startMission(missionId)
            if (response.isSuccessful) {
                Log.d("MissionRepository", "Mission started successfully: $missionId")
            } else {
                Log.e("MissionRepository", "Failed to start mission: $missionId, Error: ${response.errorBody()?.string()}")
            }
            response
        } catch (e: Exception) {
            Log.e("MissionRepository", "Exception starting mission: $missionId", e)
            throw e
        }
    }
}