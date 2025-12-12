package it.unisannio.muses.repository

import android.util.Log
import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.data.request.ChatRequest
import it.unisannio.muses.data.response.CustomGemma9
import retrofit2.Response

class ChatRepository {
    
    private val apiService = RetrofitInstance.api
    
    suspend fun generateChatResponse(prompt: String): Response<CustomGemma9> {
        return try {
            Log.d("ChatRepository", "Sending chat request with prompt: $prompt")
            val chatRequest = ChatRequest(prompt = prompt)
            val response = apiService.generateChatResponse(chatRequest)
            
            if (response.isSuccessful) {
                Log.d("ChatRepository", "Chat response received successfully: ${response.body()?.response}")
            } else {
                Log.e("ChatRepository", "Chat API error. Code: ${response.code()}, Message: ${response.errorBody()?.string()}")
            }
            
            response
        } catch (e: Exception) {
            Log.e("ChatRepository", "Exception during chat request", e)
            throw e
        }
    }
}
