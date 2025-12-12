// File: ChatRequest.kt (o all'interno di un file Models.kt)
package com.example.museo_gui // o il tuo package corretto

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    @SerializedName("prompt") // Assicura che il nome nel JSON sia "prompt"
    val userPrompt: String     // Puoi chiamare la variabile come preferisci in Kotlin
)