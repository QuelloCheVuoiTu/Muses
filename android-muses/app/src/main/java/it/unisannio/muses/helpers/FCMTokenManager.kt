package it.unisannio.muses.helpers

import android.content.Context
import android.content.SharedPreferences

class FCMTokenManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "fcm_prefs"
        private const val KEY_IS_TOKEN_SENT = "is_token_sent"
    }

    /**
     * Checks if the FCM token has been successfully sent to the server.
     * @return true if the token was sent, false otherwise.
     */
    fun isTokenSentToServer(): Boolean {
        return prefs.getBoolean(KEY_IS_TOKEN_SENT, false)
    }

    /**
     * Marks the FCM token as successfully sent to the server.
     */
    fun setTokenSentToServer(isSent: Boolean) {
        prefs.edit().putBoolean(KEY_IS_TOKEN_SENT, isSent).apply()
    }
}