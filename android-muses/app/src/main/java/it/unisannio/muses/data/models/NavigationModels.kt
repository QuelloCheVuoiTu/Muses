package it.unisannio.muses.data.models

import com.google.gson.annotations.SerializedName

data class OSRMResponse(
    val code: String,
    val routes: List<Route>,
    val waypoints: List<Waypoint>
)

data class Route(
    val legs: List<Leg>,
    @SerializedName("weight_name") val weightName: String,
    val geometry: String,
    val weight: Double,
    val duration: Double,
    val distance: Double
)

data class Leg(
    val steps: List<Any>,
    val weight: Double,
    val summary: String,
    val duration: Double,
    val distance: Double
)

data class Waypoint(
    val hint: String,
    val location: List<Double>,
    val name: String,
    val distance: Double
)