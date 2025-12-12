package it.unisannio.muses.data.models

data class TaskCompletionRequest(
    val task_id: String,
    val mission_id: String,
    val quest_id: String
)
