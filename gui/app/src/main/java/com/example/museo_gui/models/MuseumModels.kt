package com.example.museo_gui.models
data class MuseumResponse(
    val _id: String,
    val created_at: String,
    val description: String,
    val hours: String,
    val imageurl: String,
    val location: LocationData,
    val name: String,
    val price: String,
    val rating: Double,
    val parent: String? = null,
    val type: String? = null
)

data class MuseumApiResponse(
    val museums: List<MuseumResponse>? = null,
    val data: List<MuseumResponse>? = null,
    val success: Boolean? = null,
    val message: String? = null
)