package it.unisannio.muses.weather

import com.google.gson.annotations.SerializedName

/**
 * Modelli dati per l'API Open-Meteo.com
 */
data class OpenMeteoResponse(
    val current: CurrentWeather,
    val hourly: HourlyWeather?
)

data class CurrentWeather(
    @SerializedName("temperature_2m")
    val temperature: Double,
    @SerializedName("relative_humidity_2m")
    val humidity: Int,
    @SerializedName("wind_speed_10m")
    val windSpeed: Double,
    @SerializedName("weather_code")
    val weatherCode: Int,
    @SerializedName("surface_pressure")
    val pressure: Double
)

data class HourlyWeather(
    val visibility: List<Double>?
)

// Adapter per mantenere compatibilità con l'UI esistente
data class WeatherResponse(
    val weather: List<Weather>,
    val main: Main,
    val wind: Wind,
    val visibility: Int,
    val name: String,
    val dt: Long
)

data class Weather(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

data class Main(
    val temp: Double,
    val feelsLike: Double,
    val humidity: Int,
    val pressure: Int
)

data class Wind(
    val speed: Double
)