package it.unisannio.muses.data.models

import com.google.gson.annotations.SerializedName

data class UpdateUserRequest(
    val firstname: String,
    val lastname: String,
    val username: String,
    val email: String,
    // RFC3339 / ISO 8601 string expected by server, same format as CreateUserRequest
    val birthday: String,
    val country: String,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    val preferences: List<String>? = null,
    @SerializedName("range_preferences") val rangePreferences: Float? = null
)