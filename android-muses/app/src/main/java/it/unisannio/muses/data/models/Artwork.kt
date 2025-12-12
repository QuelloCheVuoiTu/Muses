package it.unisannio.muses.data.models

import com.google.gson.annotations.SerializedName

data class Artwork(
    @SerializedName("_id") val id: String,
    val name: String,
    val description: String,
    @SerializedName("imageurl") val imageUrl: String,
    val museum: String,
    @SerializedName("is_exposed") val isExposed: Boolean?,
    val types: List<String>
)