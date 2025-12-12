package it.unisannio.muses.data.api

import it.unisannio.muses.data.models.WeatherResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API interface for OpenWeatherMap weather service
 */
interface WeatherApiService {
    
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("q") cityName: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") language: String = "en"
    ): Response<WeatherResponse>
    
    @GET("weather")
    suspend fun getCurrentWeatherByCoords(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") language: String = "en"
    ): Response<WeatherResponse>
}