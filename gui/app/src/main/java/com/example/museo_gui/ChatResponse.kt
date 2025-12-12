// File: ChatResponse.kt (o all'interno di un file Models.kt)
package com.example.museo_gui // o il tuo package corretto

import com.google.gson.annotations.SerializedName

data class ChatResponse(
    @SerializedName("response") // Assicura che il nome nel JSON sia "response"
    val serverResponse: String  // Puoi chiamare la variabile come preferisci in Kotlin
)