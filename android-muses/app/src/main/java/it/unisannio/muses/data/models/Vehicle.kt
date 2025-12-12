package it.unisannio.muses.data.models

import com.google.gson.annotations.SerializedName

data class Vehicle(
    @SerializedName("_id") val id: String,
    val type: String, // "Bicicletta", "E-bike", "Monopattino"
    val pricePerHour: Double,
    val location: LocationRequestBody,
    val address: String,
    val isAvailable: Boolean = true,
    val batteryLevel: Int? = null, // Solo per veicoli elettrici
    @SerializedName("imageurl") val imageUrl: String? = null
)