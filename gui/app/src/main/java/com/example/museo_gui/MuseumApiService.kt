package com.example.museo_gui // o il tuo package corretto

import retrofit2.Response // Usa retrofit2.Response per ottenere dettagli sulla risposta HTTP
import retrofit2.http.GET
import com.example.museo_gui.models.*

interface MuseumApiService {
    @GET("getmuseums")
    suspend fun getMuseums(): Response<List<MuseumResponse>>

    @GET("getmuseums")
    suspend fun getMuseumsWrapped(): Response<MuseumApiResponse>
}