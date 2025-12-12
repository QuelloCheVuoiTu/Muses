package it.unisannio.muses.data.repositories

import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.data.models.Museum
import retrofit2.Response

class MuseumRepository {

    suspend fun getAll(): Response<List<Museum>> {
        return RetrofitInstance.api.getAllMuseums();
    }

    suspend fun getMuseumById(museumId: String): Response<Museum> {
        return RetrofitInstance.api.getMuseumById(museumId);
    }
}