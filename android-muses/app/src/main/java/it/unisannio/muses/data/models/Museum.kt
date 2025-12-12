package it.unisannio.muses.data.models

import com.google.gson.annotations.SerializedName

data class Museum(
    @SerializedName("_id") val id: String,
    val name: String,
    val description: String,
    val location: LocationRequestBody,
    val hours: String,
    val price: String,
    @SerializedName("imageurl") val imageUrl: String,
    val parent: String? = null,
    val types: List<String>
)
