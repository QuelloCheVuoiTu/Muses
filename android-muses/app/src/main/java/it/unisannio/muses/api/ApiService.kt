package it.unisannio.muses.api

import it.unisannio.muses.data.models.Artwork
import it.unisannio.muses.data.models.LocationRequestBody
import it.unisannio.muses.data.models.LoginBody
import it.unisannio.muses.data.models.FCMTokenRequestBody
import it.unisannio.muses.data.models.Museum
import it.unisannio.muses.data.models.TokenResponse
import it.unisannio.muses.data.models.User
import it.unisannio.muses.data.models.CreateUserRequest
import it.unisannio.muses.data.models.UpdateUserRequest
import it.unisannio.muses.data.models.Mission
import it.unisannio.muses.data.models.Quest
import it.unisannio.muses.data.models.QuestResponse
import it.unisannio.muses.data.models.TaskCompletionRequest
import it.unisannio.muses.data.request.ChatRequest
import it.unisannio.muses.data.response.CustomGemma9
import it.unisannio.muses.data.models.OSRMResponse
import it.unisannio.muses.data.models.RewardResponse
import it.unisannio.muses.data.models.RewardDetailsResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // Token
    @PATCH("location/savetoken")
    suspend fun sendToken(@Body token: FCMTokenRequestBody)


    // Location
    @POST("location/savelocation")
    suspend fun sendLocation(@Body location: LocationRequestBody)


    // Artworks
    @GET("artworks/search")
    suspend fun getArtworksByMuseum(@Query("museum") museumId: String): Response<List<Artwork>>

    @POST("recognition/{user_id}")
    suspend fun completeTask(
        @Path("user_id") userId: String,
        @Body taskCompletionRequest: TaskCompletionRequest
    ): Response<Unit>


    // Museum
    @GET("museums")
    suspend fun getAllMuseums(): Response<List<Museum>>

    @GET("museums/{museum_id}")
    suspend fun getMuseumById(@Path("museum_id") museumId: String): Response<Museum>


    // User
    @POST("users")
    suspend fun createUser(@Body user: CreateUserRequest): Response<Unit>

    @GET("users/{user_id}")
    suspend fun getUser(@Path("user_id") userId: String): Response<User>

    @PUT("users/{user_id}")
    suspend fun updateUser(@Path("user_id") userId: String, @Body user: UpdateUserRequest): Response<Unit>

    @GET("users/{user_id}/preferences")
    suspend fun getPreferences(@Path("user_id") userId: String): Response<com.google.gson.JsonElement>

    @PATCH("users/{user_id}/preferences")
    suspend fun patchPreferences(@Path("user_id") userId: String, @Body body: com.google.gson.JsonObject): Response<Unit>


    // Mission
    @POST("missions/{user_id}")
    suspend fun generateMission(@Path("user_id") userId: String): Response<Unit>

    @GET("missions/user/{user_id}")
    suspend fun getUserMissions(@Path("user_id") userId: String): Response<List<Mission>>

    @GET("missions/user/{user_id}")
    suspend fun getUserMissionsRaw(@Path("user_id") userId: String): Response<ResponseBody>

    @POST("missions/start/{mission_id}")
    suspend fun startMission(@Path("mission_id") missionId: String): Response<Unit>

    // Quest
    @GET("quests/{quest_id}")
    suspend fun getQuestById(@Path("quest_id") questId: String): Response<QuestResponse>

    @GET("quests/{quest_id}")
    suspend fun getQuestByIdRaw(@Path("quest_id") questId: String): Response<ResponseBody>

    @GET("quests")
    suspend fun getAllQuests(): Response<List<Quest>>

    // Auth
    @GET("auth/login")
    suspend fun login(
        @Header("Authorization") authorization: String,
        @Header("RBAC-Name") rbacName: String
    ): Response<TokenResponse>

    @POST("auth/register")
    @retrofit2.http.Headers("RBAC-Request: USER")
    suspend fun register(@Body loginBody: LoginBody): Response<Unit>

    // Chat
    @POST("chat/generate")
    suspend fun generateChatResponse(@Body chatRequest: ChatRequest): Response<CustomGemma9>

    // Navigation - OSRM route API
    @GET("osrm/route/v1/walking/{coordinates}")
    suspend fun getRoute(
        @Path("coordinates") coordinates: String,
        @Query("geometries") geometries: String = "polyline6"
    ): Response<OSRMResponse>

    // User Rewards
    @GET("user/rewards/owned/{user_id}")
    suspend fun getUserRewards(@Path("user_id") userId: String): Response<RewardResponse>
    
    @GET("user/rewards/details/{user_reward_id}")
    suspend fun getRewardDetails(@Path("user_reward_id") userRewardId: String): Response<RewardDetailsResponse>

}
