package it.unisannio.muses.data.models

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Custom deserializer to handle the malformed API response from the mission tracker service.
 * The backend incorrectly returns [missions_array, status_code] instead of just missions_array.
 */
class MissionListResponseDeserializer : JsonDeserializer<List<Mission>> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): List<Mission> {
        return when {
            json.isJsonArray -> {
                val jsonArray = json.asJsonArray
                
                // If the array has exactly 2 elements and the second is a number (status code),
                // then this is the malformed response format
                if (jsonArray.size() == 2 && jsonArray[1].isJsonPrimitive && jsonArray[1].asJsonPrimitive.isNumber) {
                    // The first element should be the actual missions array
                    val missionsElement = jsonArray[0]
                    if (missionsElement.isJsonArray) {
                        // Deserialize the missions array
                        val missionListType = object : TypeToken<List<Mission>>() {}.type
                        return context.deserialize(missionsElement, missionListType)
                    }
                }
                
                // If it's a normal array of missions, deserialize it directly
                val missionListType = object : TypeToken<List<Mission>>() {}.type
                return context.deserialize(json, missionListType)
            }
            else -> {
                // If it's not an array, return empty list
                emptyList()
            }
        }
    }
}
