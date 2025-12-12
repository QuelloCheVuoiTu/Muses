package com.example.museo_gui.models

data class User(
    val id: String,
    val mail: String,
    val password: String,
    val username: String
) {
    // Aggiungi una proprietà calcolata per compatibilità
    val email: String get() = mail
    val fullName: String get() = username // o una logica diversa se necessario

}