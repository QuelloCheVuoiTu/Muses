package com.example.museo_gui

import com.example.museo_gui.models.UsersResponse
import retrofit2.Response
import retrofit2.http.GET

interface UserApiService {
    @GET("users") // o il tuo endpoint corretto
    suspend fun getUsers(): Response<UsersResponse>
}