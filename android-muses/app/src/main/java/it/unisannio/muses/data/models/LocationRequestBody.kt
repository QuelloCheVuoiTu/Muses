package it.unisannio.muses.data.models

data class LocationRequestBody(
    val userId: String,
    val latitude: Double,
    val longitude: Double
)
