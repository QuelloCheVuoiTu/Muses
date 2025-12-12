package it.unisannio.muses.musesadmin.api

import it.unisannio.muses.musesadmin.data.models.RewardResponse
import it.unisannio.muses.musesadmin.data.models.TokenResponse
import it.unisannio.muses.musesadmin.data.models.UseRewardResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Header
import retrofit2.http.Path

interface ApiService {
    // Auth
    @GET("auth/login")
    suspend fun login(
        @Header("Authorization") authorization: String,
        @Header("RBAC-Name") rbacName: String
    ): Response<TokenResponse>

    // Rewards
    @GET("user/rewards/details/{reward_id}")
    suspend fun getRewardDetails(@Path("reward_id") rewardId: String): Response<RewardResponse>

    @POST("user/rewards/{reward_id}")
    suspend fun useReward(@Path("reward_id") rewardId: String): Response<UseRewardResponse>
}
