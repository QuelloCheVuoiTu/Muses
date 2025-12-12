// File: ChatApiService.kt
package com.example.museo_gui // o il tuo package corretto

import retrofit2.Response // Usa retrofit2.Response per ottenere dettagli sulla risposta HTTP
import retrofit2.http.Body
import retrofit2.http.POST

interface ChatApiService {
    @POST("generate") // o l'endpoint che usi
    suspend fun sendMessage(@Body request: Map<String, String>): Response<Map<String, String>>
}