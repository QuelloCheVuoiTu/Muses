package it.unisannio.muses.data.repositories

import android.util.Log
import com.google.gson.JsonParser
import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.data.models.TaskCompletionRequest
import it.unisannio.muses.data.models.Mission
import it.unisannio.muses.data.models.Quest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class TaskRepository {
    
    suspend fun completeTaskWithQR(userId: String, qrContent: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d("TaskRepository", "=== QR TASK COMPLETION START ===")
            Log.d("TaskRepository", "Processing QR for user: $userId")
            Log.d("TaskRepository", "RAW QR Content: $qrContent")

            // Estrai il task_id dal QR code
            val taskId = extractTaskIdFromQR(qrContent)
            if (taskId.isNullOrEmpty()) {
                Log.e("TaskRepository", "FAILED: Invalid QR code - task_id not found")
                return@withContext Result.failure(Exception("Invalid QR code: task_id not found"))
            }
            
            Log.d("TaskRepository", "EXTRACTED task_id: $taskId")

            // Recupera la missione attiva dell'utente
            val activeMission = getCurrentActiveMission(userId)
            if (activeMission == null) {
                Log.w("TaskRepository", "No active mission found, trying to find task in any mission")
                return@withContext findTaskInAnyMission(userId, taskId, qrContent)
            }
            
            Log.d("TaskRepository", "Found active mission: ${activeMission.id}")

            // Trova la quest che contiene questo task
            val questWithTask = findQuestByTaskId(activeMission, taskId)
            if (questWithTask == null) {
                Log.e("TaskRepository", "FAILED: Task not found in active mission")
                return@withContext Result.failure(Exception("Task not found in active mission"))
            }
            
            Log.d("TaskRepository", "Found quest with task: ${questWithTask.id}")

            // Crea la richiesta di completamento
            val request = TaskCompletionRequest(
                task_id = taskId,
                mission_id = activeMission.id,
                quest_id = questWithTask.id
            )

            // STAMPA IL JSON FINALE INTEGRATO
            Log.d("TaskRepository", "=== FINAL REQUEST JSON ===")
            Log.d("TaskRepository", "Original QR JSON: $qrContent")
            Log.d("TaskRepository", "Integrated JSON: {")
            Log.d("TaskRepository", "  \"task_id\": \"${request.task_id}\",")
            Log.d("TaskRepository", "  \"mission_id\": \"${request.mission_id}\",")
            Log.d("TaskRepository", "  \"quest_id\": \"${request.quest_id}\"")
            Log.d("TaskRepository", "}")
            Log.d("TaskRepository", "=========================")

            val response = RetrofitInstance.api.completeTask(userId, request)
            if (response.isSuccessful) {
                Log.d("TaskRepository", "SUCCESS: Task completed successfully: $taskId")
                Log.d("TaskRepository", "=== QR TASK COMPLETION END ===")
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                Log.e("TaskRepository", "FAILED: API Error ${response.code()}: ${response.message()}")
                Log.e("TaskRepository", "Response error body: $errorBody")
                Log.d("TaskRepository", "=== QR TASK COMPLETION END ===")
                Result.failure(Exception("Failed to complete task: ${response.message()}"))
            }
        } catch (e: IllegalStateException) {
            Log.e("TaskRepository", "ILLEGAL STATE EXCEPTION: ${e.message}")
            Log.e("TaskRepository", "Stack trace:", e)
            Log.d("TaskRepository", "=== QR TASK COMPLETION END ===")
            Result.failure(Exception("IllegalStateException: ${e.message}"))
        } catch (e: Exception) {
            Log.e("TaskRepository", "GENERAL EXCEPTION: Error completing task with QR", e)
            Log.d("TaskRepository", "=== QR TASK COMPLETION END ===")
            Result.failure(e)
        }
    }

    private fun extractTaskIdFromQR(qrContent: String): String? {
        return try {
            Log.d("TaskRepository", "=== QR PARSING ===")
            Log.d("TaskRepository", "Raw QR content: '$qrContent'")
            
            // Prova prima a parsare come JSON
            val jsonElement = JsonParser.parseString(qrContent)
            if (jsonElement.isJsonObject) {
                val taskId = jsonElement.asJsonObject.get("task_id")?.asString
                Log.d("TaskRepository", "Parsed as JSON - task_id: '$taskId'")
                Log.d("TaskRepository", "Full JSON object: $jsonElement")
                taskId
            } else {
                // Se non è JSON, assumiamo che il QR contenga solo il task_id
                val taskId = qrContent.trim()
                Log.d("TaskRepository", "Not JSON - using raw content as task_id: '$taskId'")
                taskId
            }
        } catch (e: Exception) {
            // Se il parsing JSON fallisce, assumiamo che il contenuto sia il task_id
            val taskId = qrContent.trim()
            Log.d("TaskRepository", "JSON parsing failed - using raw content as task_id: '$taskId'")
            Log.d("TaskRepository", "Parse exception: ${e.message}")
            taskId
        }.also {
            Log.d("TaskRepository", "Final extracted task_id: '$it'")
            Log.d("TaskRepository", "==================")
        }
    }

    private suspend fun getCurrentActiveMission(userId: String): Mission? {
        return try {
            Log.d("TaskRepository", "Getting missions for user: $userId")
            
            // Usiamo direttamente il raw response per parsare la struttura particolare
            val rawResponse = RetrofitInstance.api.getUserMissionsRaw(userId)
            if (rawResponse.isSuccessful) {
                val rawBody = rawResponse.body()?.string() ?: ""
                Log.d("TaskRepository", "RAW MISSIONS RESPONSE: $rawBody")
                
                // Se la risposta è vuota, non ci sono missioni
                if (rawBody.isEmpty() || rawBody.trim() == "[]" || rawBody.trim() == "null") {
                    Log.w("TaskRepository", "No missions found for user")
                    return null
                }
                
                // Parsa manualmente il formato [[missions...], statusCode]
                val jsonElement = JsonParser.parseString(rawBody)
                if (jsonElement.isJsonArray) {
                    val outerArray = jsonElement.asJsonArray
                    if (outerArray.size() >= 2 && outerArray[0].isJsonArray) {
                        // È nel formato [[missions...], statusCode]
                        val missionsArray = outerArray[0].asJsonArray
                        Log.d("TaskRepository", "Found ${missionsArray.size()} missions in nested array")
                        
                        val missions = mutableListOf<Mission>()
                        for (missionElement in missionsArray) {
                            try {
                                val mission = com.google.gson.Gson().fromJson(missionElement, Mission::class.java)
                                missions.add(mission)
                                Log.d("TaskRepository", "Mission: ${mission.id}, Status: ${mission.status}")
                            } catch (e: Exception) {
                                Log.e("TaskRepository", "Error parsing mission: $missionElement", e)
                            }
                        }
                        
                        val activeMission = missions.find { mission -> 
                            mission.status.equals("ACTIVE", ignoreCase = true) || 
                            mission.status.equals("IN_PROGRESS", ignoreCase = true) ||
                            mission.status.equals("STARTED", ignoreCase = true)
                        }
                        
                        if (activeMission != null) {
                            Log.d("TaskRepository", "Found active mission: ${activeMission.id} with status: ${activeMission.status}")
                        } else {
                            Log.w("TaskRepository", "No active mission found. Available missions statuses: ${missions.map { it.status }}")
                        }
                        
                        return activeMission
                    }
                }
            }
            
            Log.e("TaskRepository", "Failed to parse missions response")
            null
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error getting active mission", e)
            null
        }
    }
    
    private suspend fun findTaskInAnyMission(userId: String, taskId: String, originalQrContent: String): Result<Unit> {
        return try {
            Log.d("TaskRepository", "=== FALLBACK SEARCH IN ALL MISSIONS ===")
            
            // Usa il raw response anche qui per parsing manuale
            val rawResponse = RetrofitInstance.api.getUserMissionsRaw(userId)
            if (rawResponse.isSuccessful) {
                val rawBody = rawResponse.body()?.string() ?: ""
                Log.d("TaskRepository", "RAW FALLBACK MISSIONS RESPONSE: $rawBody")
                
                // Parsa manualmente il formato [[missions...], statusCode]
                val jsonElement = JsonParser.parseString(rawBody)
                if (jsonElement.isJsonArray) {
                    val outerArray = jsonElement.asJsonArray
                    if (outerArray.size() >= 2 && outerArray[0].isJsonArray) {
                        val missionsArray = outerArray[0].asJsonArray
                        
                        for (missionElement in missionsArray) {
                            try {
                                val mission = com.google.gson.Gson().fromJson(missionElement, Mission::class.java)
                                Log.d("TaskRepository", "Searching task $taskId in mission ${mission.id} (status: ${mission.status})")
                                
                                val quest = findQuestByTaskId(mission, taskId)
                                if (quest != null) {
                                    Log.d("TaskRepository", "Found task in mission ${mission.id}, quest ${quest.id}")
                                    
                                    val request = TaskCompletionRequest(
                                        task_id = taskId,
                                        mission_id = mission.id,
                                        quest_id = quest.id
                                    )

                                    // STAMPA IL JSON FINALE INTEGRATO (FALLBACK)
                                    Log.d("TaskRepository", "=== FALLBACK REQUEST JSON ===")
                                    Log.d("TaskRepository", "Original QR JSON: $originalQrContent")
                                    Log.d("TaskRepository", "Integrated JSON: {")
                                    Log.d("TaskRepository", "  \"task_id\": \"${request.task_id}\",")
                                    Log.d("TaskRepository", "  \"mission_id\": \"${request.mission_id}\",")
                                    Log.d("TaskRepository", "  \"quest_id\": \"${request.quest_id}\"")
                                    Log.d("TaskRepository", "}")
                                    Log.d("TaskRepository", "==============================")

                                    val apiResponse = RetrofitInstance.api.completeTask(userId, request)
                                    if (apiResponse.isSuccessful) {
                                        Log.d("TaskRepository", "SUCCESS: Task completed successfully (fallback)")
                                        return Result.success(Unit)
                                    } else {
                                        val errorBody = apiResponse.errorBody()?.string() ?: "No error body"
                                        Log.e("TaskRepository", "FAILED: API Error ${apiResponse.code()}: ${apiResponse.message()}")
                                        Log.e("TaskRepository", "Response error body: $errorBody")
                                        return Result.failure(Exception("Failed to complete task: ${apiResponse.message()}"))
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("TaskRepository", "Error parsing mission in fallback: $missionElement", e)
                            }
                        }
                    }
                }
                
                Result.failure(Exception("Task $taskId not found in any mission"))
            } else {
                Result.failure(Exception("Failed to get user missions: ${rawResponse.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun findQuestByTaskId(mission: Mission, taskId: String): Quest? {
        return try {
            Log.d("TaskRepository", "Looking for task $taskId in mission ${mission.id}")
            Log.d("TaskRepository", "Mission has ${mission.steps.size} steps")
            
            // Assumendo che Mission abbia una lista di step IDs che corrispondono ai quest IDs
            for (step in mission.steps) {
                val questId = step.stepId
                Log.d("TaskRepository", "Checking quest: $questId")
                
                val response = RetrofitInstance.api.getQuestById(questId)
                if (response.isSuccessful) {
                    val questResponse = response.body()
                    val quest = questResponse?.quest
                    if (quest != null) {
                        Log.d("TaskRepository", "=== QUEST STRUCTURE ANALYSIS ===")
                        Log.d("TaskRepository", "Quest ID: ${quest.id}")
                        Log.d("TaskRepository", "Quest Title: ${quest.title}")
                        Log.d("TaskRepository", "Quest Status: ${quest.status}")
                        Log.d("TaskRepository", "Quest has ${quest.tasks.size} tasks:")
                        
                        quest.tasks.forEach { (id, task) ->
                            Log.d("TaskRepository", "  Task ID: $id")
                            Log.d("TaskRepository", "    Title: ${task.title}")
                            Log.d("TaskRepository", "    Description: ${task.description}")
                            Log.d("TaskRepository", "    Completed: ${task.completed}")
                            if (id == taskId) {
                                Log.d("TaskRepository", "    *** THIS IS THE TARGET TASK! ***")
                            }
                        }
                        Log.d("TaskRepository", "================================")
                        
                        if (quest.tasks.containsKey(taskId)) {
                            Log.d("TaskRepository", "✅ FOUND task $taskId in quest ${quest.id}")
                            return quest
                        } else {
                            Log.w("TaskRepository", "❌ Task $taskId NOT found in quest ${quest.id}")
                        }
                    } else {
                        Log.w("TaskRepository", "Quest response body is null for quest $questId")
                    }
                } else {
                    Log.e("TaskRepository", "Failed to get quest $questId: ${response.code()}")
                }
            }
            Log.w("TaskRepository", "Task $taskId not found in any quest of mission ${mission.id}")
            null
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error finding quest by task ID", e)
            null
        }
    }
    
    // Manteniamo il metodo originale per compatibilità
    suspend fun completeTask(userId: String, taskId: String, missionId: String, questId: String, qrContent: String): Response<Unit> {
        Log.d("TaskRepository", "Completing task - User: $userId, Task: $taskId, Mission: $missionId, Quest: $questId")
        
        return try {
            val request = TaskCompletionRequest(
                task_id = taskId,
                mission_id = missionId,
                quest_id = questId
            )
            
            val response = RetrofitInstance.api.completeTask(userId, request)
            
            if (response.isSuccessful) {
                Log.d("TaskRepository", "Task completed successfully: $taskId")
            } else {
                val errorBody = try {
                    response.errorBody()?.string() ?: "No error body"
                } catch (e: Exception) {
                    "Error reading error body: ${e.message}"
                }
                Log.e("TaskRepository", "Failed to complete task: $taskId, Error: $errorBody")
            }
            
            response
        } catch (e: Exception) {
            Log.e("TaskRepository", "Exception completing task: $taskId", e)
            throw e
        }
    }
}