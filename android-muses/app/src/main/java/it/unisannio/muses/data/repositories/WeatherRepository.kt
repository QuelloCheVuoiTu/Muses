package it.unisannio.muses.data.repositories

import android.util.Log
import it.unisannio.muses.data.api.WeatherApiService
import it.unisannio.muses.data.models.WeatherResponse
import it.unisannio.muses.data.models.Weather
import it.unisannio.muses.data.models.Main
import it.unisannio.muses.data.models.Wind
import it.unisannio.muses.data.models.Sys
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Repository for weather data operations
 */
class WeatherRepository {
    
    companion object {
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5/"
        // Demo API key for OpenWeatherMap - for production use, get your own from openweathermap.org
        private const val API_KEY = "demo_key"
        private const val TAG = "WeatherRepository"
        
        // Default coordinates for Benevento, Italy
        private const val BENEVENTO_LAT = 41.1290
        private const val BENEVENTO_LON = 14.7700
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val weatherApi = retrofit.create(WeatherApiService::class.java)
    
    /**
     * Get current weather for Benevento using coordinates
     */
    suspend fun getCurrentWeatherForBenevento(): Response<WeatherResponse> {
        return try {
            Log.d(TAG, "Fetching weather for Benevento (lat: $BENEVENTO_LAT, lon: $BENEVENTO_LON)")
            
            // For demo purposes, return mock data
            // In production, uncomment the real API call below and get a real API key
            val mockWeatherResponse = createMockWeatherResponse()
            Response.success(mockWeatherResponse)
            
            // Real API call (uncomment when you have a valid API key):
            // weatherApi.getCurrentWeatherByCoords(
            //     latitude = BENEVENTO_LAT,
            //     longitude = BENEVENTO_LON,
            //     apiKey = API_KEY
            // )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching weather data", e)
            throw e
        }
    }
    
    private fun createMockWeatherResponse(): WeatherResponse {
        return WeatherResponse(
            weather = listOf(
                Weather(
                    id = 800,
                    main = "Clear",
                    description = "clear sky",
                    icon = "01d"
                )
            ),
            main = Main(
                temp = 22.5,
                feelsLike = 24.2,
                tempMin = 18.0,
                tempMax = 26.0,
                pressure = 1013,
                humidity = 65
            ),
            visibility = 10000,
            wind = Wind(
                speed = 3.2,
                deg = 220
            ),
            dt = System.currentTimeMillis() / 1000,
            sys = Sys(
                type = 1,
                id = 9717,
                country = "IT",
                sunrise = System.currentTimeMillis() / 1000 - 7200,
                sunset = System.currentTimeMillis() / 1000 + 7200
            ),
            timezone = 7200,
            id = 3182162,
            name = "Benevento",
            cod = 200
        )
    }
    
    /**
     * Get current weather for a specific city
     */
    suspend fun getCurrentWeatherForCity(cityName: String): Response<WeatherResponse> {
        return try {
            Log.d(TAG, "Fetching weather for city: $cityName")
            weatherApi.getCurrentWeather(
                cityName = cityName,
                apiKey = API_KEY
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching weather data for city: $cityName", e)
            throw e
        }
    }
}