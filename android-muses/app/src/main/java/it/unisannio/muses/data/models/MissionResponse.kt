package it.unisannio.muses.data.models

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonArray
import com.google.gson.annotations.JsonAdapter
import java.lang.reflect.Type

@JsonAdapter(MissionResponseDeserializer::class)
data class MissionResponse(
    val missions: List<Mission>,
    val statusCode: Int
)

class MissionResponseDeserializer : JsonDeserializer<List<Mission>> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): List<Mission> {
        return when {
            json.isJsonArray -> {
                val jsonArray = json.asJsonArray
                if (jsonArray.size() >= 2 && jsonArray[0].isJsonArray) {
                    // Formato [[missions...], statusCode]
                    val missionsArray = jsonArray[0].asJsonArray
                    missionsArray.map { missionElement ->
                        context.deserialize<Mission>(missionElement, Mission::class.java)
                    }
                } else {
                    // Formato normale [missions...]
                    jsonArray.map { missionElement ->
                        context.deserialize<Mission>(missionElement, Mission::class.java)
                    }
                }
            }
            else -> emptyList()
        }
    }
}
