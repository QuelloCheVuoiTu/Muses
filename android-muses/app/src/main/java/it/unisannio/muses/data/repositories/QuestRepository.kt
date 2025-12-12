package it.unisannio.muses.data.repositories

import android.util.Log
import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.data.models.Quest
import it.unisannio.muses.data.models.QuestResponse
import okhttp3.ResponseBody
import retrofit2.Response

class QuestRepository {
    suspend fun getQuestById(questId: String): Response<Quest> {
        Log.d("QuestRepository", "Requesting quest with ID: $questId")
        
        try {
            val response = RetrofitInstance.api.getQuestById(questId)
            Log.d("QuestRepository", "Quest API response - Code: ${response.code()}, Success: ${response.isSuccessful}")
            
            if (response.isSuccessful) {
                val questResponse = response.body()
                if (questResponse != null) {
                    val quest = questResponse.quest
                    Log.d("QuestRepository", "Quest parsed successfully:")
                    Log.d("QuestRepository", "  ID: ${quest.id}")
                    Log.d("QuestRepository", "  Title: '${quest.title}'")
                    Log.d("QuestRepository", "  Description length: ${quest.description?.length ?: 0}")
                    Log.d("QuestRepository", "  Status: ${quest.status}")
                    Log.d("QuestRepository", "  Subject ID: ${quest.subjectId}")
                    Log.d("QuestRepository", "  Tasks count: ${quest.tasks.size}")
                    Log.d("QuestRepository", "  Tasks completed: ${questResponse.tasksCompleted}/${questResponse.totalTasks}")
                    quest.tasks.forEach { (taskId, task) ->
                        Log.d("QuestRepository", "    Task $taskId: '${task.title}' - completed: ${task.completed}")
                    }
                    
                    // Return a Response<Quest> by extracting the quest from the wrapper
                    return Response.success(quest, response.raw())
                } else {
                    Log.w("QuestRepository", "Quest response body is null")
                    // Return the original response but cast as Quest (it will be null)
                    @Suppress("UNCHECKED_CAST")
                    return response as Response<Quest>
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("QuestRepository", "Quest API error: $errorBody")
                // Return error response
                @Suppress("UNCHECKED_CAST")
                return response as Response<Quest>
            }
            
        } catch (e: Exception) {
            Log.e("QuestRepository", "Exception in getQuestById", e)
            throw e
        }
    }

    suspend fun getAllQuests(): Response<List<Quest>> {
        Log.d("QuestRepository", "Requesting all quests")
        val response = RetrofitInstance.api.getAllQuests()
        Log.d("QuestRepository", "All quests API response - Code: ${response.code()}, Success: ${response.isSuccessful}")
        return response
    }

    suspend fun getQuestByIdRaw(questId: String): Response<ResponseBody> {
        Log.d("QuestRepository", "Requesting raw quest with ID: $questId")
        return RetrofitInstance.api.getQuestByIdRaw(questId)
    }
}
