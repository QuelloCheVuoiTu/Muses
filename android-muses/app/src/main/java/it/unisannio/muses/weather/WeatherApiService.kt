package it.unisannio.muses.weather

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interfaccia API per Open-Meteo.com (completamente gratuita, nessuna API key richiesta)
 */
interface WeatherApiService {
    
    @GET("forecast")
    fun getCurrentWeather(
        @Query("latitude") lat: Double = 41.1297, // Benevento latitude
        @Query("longitude") lon: Double = 14.7697, // Benevento longitude
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code,surface_pressure",
        @Query("hourly") hourly: String = "visibility",
        @Query("timezone") timezone: String = "Europe/Rome"
    ): Call<OpenMeteoResponse>
    
    @GET("forecast")
    fun getCurrentWeatherForLocation(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code,surface_pressure",
        @Query("hourly") hourly: String = "visibility",
        @Query("timezone") timezone: String = "Europe/Rome"
    ): Call<OpenMeteoResponse>
    
    companion object {
        const val BASE_URL = "https://api.open-meteo.com/v1/"
        // Nessuna API key necessaria - completamente gratuita!
    }
}