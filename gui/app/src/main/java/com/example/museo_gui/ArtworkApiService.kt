package com.example.museo_gui

import com.example.museo_gui.models.Artwork
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ArtworkApiService {
    @GET("getoperebymuseum/{museumName}")
    suspend fun getArtworksByMuseum(@Path("museumName") museumName: String): Response<List<Artwork>>
}