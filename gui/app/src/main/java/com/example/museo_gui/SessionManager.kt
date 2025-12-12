package com.example.museo_gui

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.museo_gui.models.User

class SessionManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SessionManager"
        private const val PREF_NAME = "user_session"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    fun saveUserSession(user: User) {
        val editor = sharedPreferences.edit()

        Log.d(TAG, "Salvando sessione utente...")
        Log.d(TAG, "Username: ${user.username}")
        Log.d(TAG, "Email: ${user.mail}")
        Log.d(TAG, "Password: ${user.password}")
        Log.d(TAG, "User ID: ${user.id}")

        editor.putString(KEY_USERNAME, user.username)
        editor.putString(KEY_EMAIL, user.mail)
        editor.putString(KEY_PASSWORD, user.password)
        editor.putString(KEY_USER_ID, user.id)
        editor.putBoolean(KEY_IS_LOGGED_IN, true)

        val success = editor.commit() // Usa commit() invece di apply() per verificare il successo

        Log.d(TAG, "Salvataggio completato: $success")

        // Verifica immediata del salvataggio
        Log.d(TAG, "Verifica salvataggio:")
        Log.d(TAG, "Username salvato: '${getUsername()}'")
        Log.d(TAG, "Email salvata: '${getEmail()}'")
        Log.d(TAG, "Password salvata: '${getPassword()}'")
        Log.d(TAG, "User ID salvato: '${getUserId()}'")
        Log.d(TAG, "Is logged in: ${isLoggedIn()}")
    }

    fun getUsername(): String? {
        val username = sharedPreferences.getString(KEY_USERNAME, null)
        Log.d(TAG, "getUsername() restituisce: '$username'")
        return username
    }

    fun getEmail(): String? {
        val email = sharedPreferences.getString(KEY_EMAIL, null)
        Log.d(TAG, "getEmail() restituisce: '$email'")
        return email
    }

    fun getPassword(): String? {
        val password = sharedPreferences.getString(KEY_PASSWORD, null)
        Log.d(TAG, "getPassword() restituisce: '$password'")
        return password
    }

    fun getUserId(): String? {
        val userId = sharedPreferences.getString(KEY_USER_ID, null)
        Log.d(TAG, "getUserId() restituisce: '$userId'")
        return userId
    }

    fun isLoggedIn(): Boolean {
        val isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        Log.d(TAG, "isLoggedIn() restituisce: $isLoggedIn")
        return isLoggedIn
    }
    fun logout() {
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()
    }
}