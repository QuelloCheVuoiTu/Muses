package it.unisannio.muses.data.repositories

import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.data.models.Artwork
import retrofit2.Response

class ArtworkRepository {

    suspend fun getArtworksByMuseum(museumId: String): Response<List<Artwork>> {
        return RetrofitInstance.api.getArtworksByMuseum(museumId);
    }
}