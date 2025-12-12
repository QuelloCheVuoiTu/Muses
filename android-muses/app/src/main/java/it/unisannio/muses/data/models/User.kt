package it.unisannio.muses.data.models

import com.google.gson.annotations.SerializedName
import java.util.Date

data class User(
    @SerializedName("_id") val id: String,
    val firstname: String,
    val lastname: String,
    val username: String,
    val email: String,
    val birthday: Date,
    val country: String,
    @SerializedName("avatar_url")val avatarUrl: String?,
    val preferences: List<String>?,
    @SerializedName("range_preferences") val rangePreferences: Float?
)
