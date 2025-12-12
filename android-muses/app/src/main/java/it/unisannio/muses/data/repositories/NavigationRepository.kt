package it.unisannio.muses.data.repositories

import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.data.models.OSRMResponse
import retrofit2.Response

class NavigationRepository {

    suspend fun getRoute(userLong: Double, userLat: Double, museumLong: Double, museumLat: Double): Response<OSRMResponse> {
        val coordinates = "$userLong,$userLat;$museumLong,$museumLat"
        return RetrofitInstance.api.getRoute(coordinates, "polyline6")
    }
}