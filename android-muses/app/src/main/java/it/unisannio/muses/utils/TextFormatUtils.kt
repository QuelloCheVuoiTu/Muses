package it.unisannio.muses.utils

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

object TextFormatUtils {
    
    /**
     * Estrae il testo dal campo response in una stringa JSON
     * @param jsonString Stringa che potrebbe contenere JSON come {"response":"testo effettivo"}
     * @return Il testo estratto dal campo response, oppure la stringa originale se non è JSON
     */
    fun extractTextFromJsonResponse(jsonString: String?): String {
        if (jsonString.isNullOrBlank()) return ""
        
        // Prova a parsare come JSON
        try {
            val jsonElement = JsonParser.parseString(jsonString.trim())
            if (jsonElement.isJsonObject) {
                val jsonObject = jsonElement.asJsonObject
                if (jsonObject.has("response")) {
                    val responseText = jsonObject.get("response").asString
                    // Pulisci caratteri di escape e newline multipli (compatibile API 24)
                    return responseText
                        .replace("\\n", "\n")
                        .replace("\n\n", "\n")
                        .replace(Regex("\\n+"), "\n")
                        .trim()
                }
            }
        } catch (e: JsonSyntaxException) {
            // Se non è un JSON valido, restituisci la stringa originale
            return jsonString.trim()
        } catch (e: Exception) {
            // In caso di altri errori, restituisci la stringa originale
            return jsonString.trim()
        }
        
        // Se arriviamo qui, restituisci la stringa originale
        return jsonString.trim()
    }
    
    /**
     * Formatta il titolo di una quest o task
     */
    fun formatTitle(title: String): String {
        if (title.isEmpty()) return "Titolo non disponibile"
        
        val extractedText = extractTextFromJsonResponse(title)
        return if (extractedText.isNotEmpty()) extractedText else "Titolo non disponibile"
    }
    
    /**
     * Formatta la descrizione di una quest o task
     */
    fun formatDescription(description: String): String {
        if (description.isEmpty()) return "Nessuna descrizione disponibile."
        
        val extractedText = extractTextFromJsonResponse(description)
        return if (extractedText.isNotEmpty()) extractedText else "Nessuna descrizione disponibile."
    }
}