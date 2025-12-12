package it.unisannio.muses.weather

import android.content.Context
import android.content.SharedPreferences
import android.location.Geocoder
import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.Locale

/**
 * Repository per gestire i dati meteo con cache oraria
 */
class WeatherRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "WeatherRepository"
        private const val PREFS_NAME = "weather_cache"
        private const val KEY_LAST_UPDATE = "last_update"
        private const val KEY_CACHED_DATA = "cached_weather_data"
        private const val CACHE_DURATION_HOURS = 1L
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val weatherApi: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(WeatherApiService.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }
    
    interface WeatherCallback {
        fun onSuccess(weather: WeatherResponse)
        fun onError(message: String)
        fun onLoading()
    }
    
    fun getWeatherData(callback: WeatherCallback) {
        // COMMENTATO clearCache() - ora usa cache oraria normale
        // clearCache()
        
        // Controlla se abbiamo dati in cache validi
        if (isCacheValid()) {
            val cachedData = getCachedWeatherData()
            if (cachedData != null) {
                Log.d(TAG, "Usando dati dalla cache")
                callback.onSuccess(cachedData)
                return
            }
        }
        
        // Dati non in cache o scaduti, chiamata API
        callback.onLoading()
        fetchWeatherFromApi(callback)
    }
    
    private fun clearCache() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Cache cancellata - forzato aggiornamento API")
    }
    
    private fun fetchWeatherFromApi(callback: WeatherRepository.WeatherCallback) {
        Log.d(TAG, "Chiamata API Open-Meteo per Benevento (41.1297, 14.7697)")
        Log.d(TAG, "URL: ${WeatherApiService.BASE_URL}forecast?latitude=41.1297&longitude=14.7697&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code,surface_pressure&timezone=Europe/Rome")
        
        // Chiamata API Open-Meteo (completamente gratuita)
        weatherApi.getCurrentWeather().enqueue(object : Callback<OpenMeteoResponse> {
            override fun onResponse(call: Call<OpenMeteoResponse>, response: Response<OpenMeteoResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val openMeteoData = response.body()!!
                    Log.d(TAG, "✅ SUCCESS: temp=${openMeteoData.current.temperature}°C, umidità=${openMeteoData.current.humidity}%")
                    
                    // Converto i dati Open-Meteo nel formato compatibile con l'UI
                    val weatherData = convertToWeatherResponse(openMeteoData, "Benevento, Italia")
                    cacheWeatherData(weatherData)
                    callback.onSuccess(weatherData)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "No error details"
                    val errorMessage = "❌ API Error: ${response.code()} - ${response.message()}\n$errorBody"
                    Log.e(TAG, errorMessage)
                    callback.onError(errorMessage)
                }
            }
            
            override fun onFailure(call: Call<OpenMeteoResponse>, t: Throwable) {
                val errorMessage = "❌ Network Error: ${t.message}"
                Log.e(TAG, errorMessage, t)
                callback.onError(errorMessage)
            }
        })
    }
    
    fun getWeatherDataForLocation(latitude: Double, longitude: Double, locationName: String, callback: WeatherCallback) {
        // Per le coordinate GPS, non utilizziamo cache poiché la posizione può cambiare
        Log.d(TAG, "Richiesta meteo per $locationName (lat: $latitude, lon: $longitude)")
        
        callback.onLoading()
        
        // Se il locationName è "Posizione Attuale", ottieni il nome reale dalla posizione
        if (locationName == "Posizione Attuale") {
            getCityNameFromCoordinates(latitude, longitude) { cityName ->
                fetchWeatherFromApiForLocation(latitude, longitude, cityName, callback)
            }
        } else {
            fetchWeatherFromApiForLocation(latitude, longitude, locationName, callback)
        }
    }
    
    private fun fetchWeatherFromApiForLocation(latitude: Double, longitude: Double, locationName: String, callback: WeatherCallback) {
        Log.d(TAG, "Chiamata API Open-Meteo per $locationName ($latitude, $longitude)")
        Log.d(TAG, "URL: ${WeatherApiService.BASE_URL}forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code,surface_pressure&timezone=Europe/Rome")
        
        // Chiamata API Open-Meteo con coordinate dinamiche
        weatherApi.getCurrentWeatherForLocation(latitude, longitude).enqueue(object : Callback<OpenMeteoResponse> {
            override fun onResponse(call: Call<OpenMeteoResponse>, response: Response<OpenMeteoResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val openMeteoData = response.body()!!
                    Log.d(TAG, "✅ SUCCESS per $locationName: temp=${openMeteoData.current.temperature}°C, umidità=${openMeteoData.current.humidity}%")
                    
                    // Converto i dati Open-Meteo nel formato compatibile con l'UI
                    val weatherData = convertToWeatherResponse(openMeteoData, locationName)
                    callback.onSuccess(weatherData)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "No error details"
                    val errorMessage = "❌ API Error per $locationName: ${response.code()} - ${response.message()}\n$errorBody"
                    Log.e(TAG, errorMessage)
                    callback.onError(errorMessage)
                }
            }
            
            override fun onFailure(call: Call<OpenMeteoResponse>, t: Throwable) {
                val errorMessage = "❌ Network Error per $locationName: ${t.message}"
                Log.e(TAG, errorMessage, t)
                callback.onError(errorMessage)
            }
        })
    }
    
    private fun convertToWeatherResponse(openMeteoData: OpenMeteoResponse, locationName: String): WeatherResponse {
        val current = openMeteoData.current
        
        // Converto il weather code di Open-Meteo in descrizione
        val (weatherMain, weatherDesc) = getWeatherDescription(current.weatherCode)
        
        // Calcolo feels like (approssimato)
        val feelsLike = current.temperature + (current.humidity - 60) * 0.1
        
        // Visibilità (usa primo valore se disponibile, altrimenti default)
        val visibility = openMeteoData.hourly?.visibility?.firstOrNull()?.times(1000)?.toInt() ?: 10000
        
        return WeatherResponse(
            weather = listOf(
                Weather(
                    id = current.weatherCode,
                    main = weatherMain,
                    description = weatherDesc,
                    icon = getWeatherIcon(current.weatherCode)
                )
            ),
            main = Main(
                temp = current.temperature,
                feelsLike = feelsLike,
                humidity = current.humidity,
                pressure = current.pressure.toInt()
            ),
            wind = Wind(speed = current.windSpeed),
            visibility = visibility,
            name = locationName,
            dt = System.currentTimeMillis() / 1000
        )
    }
    
    private fun getWeatherDescription(code: Int): Pair<String, String> {
        return when (code) {
            0 -> Pair("Clear", "cielo sereno")
            1, 2, 3 -> Pair("Clouds", "parzialmente nuvoloso")
            45, 48 -> Pair("Fog", "nebbia")
            51, 53, 55 -> Pair("Drizzle", "pioggerella")
            61, 63, 65 -> Pair("Rain", "pioggia")
            71, 73, 75 -> Pair("Snow", "neve")
            77 -> Pair("Snow", "granelli di neve")
            80, 81, 82 -> Pair("Rain", "rovesci di pioggia")
            85, 86 -> Pair("Snow", "rovesci di neve")
            95 -> Pair("Thunderstorm", "temporale")
            96, 99 -> Pair("Thunderstorm", "temporale con grandine")
            else -> Pair("Clear", "condizioni variabili")
        }
    }
    
    private fun getWeatherIcon(code: Int): String {
        return when (code) {
            0 -> "01d" // sole
            1, 2, 3 -> "02d" // nuvole
            45, 48 -> "50d" // nebbia
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> "10d" // pioggia
            71, 73, 75, 77, 85, 86 -> "13d" // neve
            95, 96, 99 -> "11d" // temporale
            else -> "01d"
        }
    }
    
    private fun isCacheValid(): Boolean {
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        val now = System.currentTimeMillis()
        val cacheAge = now - lastUpdate
        val cacheAgeHours = TimeUnit.MILLISECONDS.toHours(cacheAge)
        
        return cacheAgeHours < CACHE_DURATION_HOURS
    }
    
    private fun getCachedWeatherData(): WeatherResponse? {
        val jsonData = prefs.getString(KEY_CACHED_DATA, null)
        return if (jsonData != null) {
            try {
                com.google.gson.Gson().fromJson(jsonData, WeatherResponse::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing cached data", e)
                null
            }
        } else null
    }
    
    private fun cacheWeatherData(weatherData: WeatherResponse) {
        val json = com.google.gson.Gson().toJson(weatherData)
        prefs.edit()
            .putString(KEY_CACHED_DATA, json)
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "Dati meteo salvati in cache")
    }
    
    private fun getCityNameFromCoordinates(latitude: Double, longitude: Double, callback: (String) -> Unit) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Geocoder.isPresent()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    // Usa la nuova API asincrona per Android 33+
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        try {
                            if (!addresses.isNullOrEmpty()) {
                                val address = addresses[0]
                                val cityName = when {
                                    !address.locality.isNullOrBlank() -> "${address.locality}, ${address.countryName ?: ""}"
                                    !address.subAdminArea.isNullOrBlank() -> "${address.subAdminArea}, ${address.countryName ?: ""}"
                                    !address.adminArea.isNullOrBlank() -> "${address.adminArea}, ${address.countryName ?: ""}"
                                    !address.countryName.isNullOrBlank() -> address.countryName
                                    else -> "Posizione Sconosciuta"
                                }
                                Log.d(TAG, "Nome città ottenuto: $cityName")
                                callback(cityName)
                            } else {
                                Log.w(TAG, "Nessun indirizzo trovato per le coordinate")
                                callback("Posizione Attuale")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Errore nel reverse geocoding: ${e.message}")
                            callback("Posizione Attuale")
                        }
                    }
                } else {
                    // Usa l'API legacy per versioni Android precedenti
                    Thread {
                        try {
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val address = addresses[0]
                                val cityName = when {
                                    !address.locality.isNullOrBlank() -> "${address.locality}, ${address.countryName ?: ""}"
                                    !address.subAdminArea.isNullOrBlank() -> "${address.subAdminArea}, ${address.countryName ?: ""}"
                                    !address.adminArea.isNullOrBlank() -> "${address.adminArea}, ${address.countryName ?: ""}"
                                    !address.countryName.isNullOrBlank() -> address.countryName
                                    else -> "Posizione Sconosciuta"
                                }
                                Log.d(TAG, "Nome città ottenuto: $cityName")
                                callback(cityName)
                            } else {
                                Log.w(TAG, "Nessun indirizzo trovato per le coordinate")
                                callback("Posizione Attuale")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Errore nel reverse geocoding: ${e.message}")
                            callback("Posizione Attuale")
                        }
                    }.start()
                }
            } else {
                Log.w(TAG, "Geocoder non disponibile")
                callback("Posizione Attuale")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'inizializzazione del Geocoder: ${e.message}")
            callback("Posizione Attuale")
        }
    }
}