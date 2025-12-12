package it.unisannio.muses.data.models

import com.google.gson.annotations.SerializedName

data class QuestResponse(
    val quest: Quest,
    @SerializedName("tasks_completed") val tasksCompleted: Int,
    @SerializedName("tot_tasks") val totalTasks: Int
)
