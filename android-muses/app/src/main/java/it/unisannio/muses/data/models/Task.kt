package it.unisannio.muses.data.models

import com.google.gson.annotations.SerializedName

data class Task(
	val completed: Boolean,
	val description: String,
	val title: String
)