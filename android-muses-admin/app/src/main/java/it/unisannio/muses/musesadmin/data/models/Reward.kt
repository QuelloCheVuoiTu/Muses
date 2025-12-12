package it.unisannio.muses.musesadmin.data.models

import java.io.Serializable

data class Reward(
    val amount: Int,
    val description: String,
    val museum_id: String,
    val reduction_type: String,
    val subject: String
) : Serializable