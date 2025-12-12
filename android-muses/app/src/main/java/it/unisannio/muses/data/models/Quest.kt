package it.unisannio.muses.data.models

import com.google.gson.annotations.SerializedName

data class Quest(
	@SerializedName("_id") val id: String,
	val title: String,
	val description: String,
	val status: String,
	@SerializedName("subject_id") val subjectId: String,
	val tasks: Map<String, Task>
)
