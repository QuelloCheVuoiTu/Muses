package it.unisannio.muses.helpers

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple manager to persist the bearer/auth token returned by the backend.
 */
class AuthTokenManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "auth_prefs"
        private const val KEY_BEARER_TOKEN = "bearer_token"
        private const val KEY_ENTITY_ID = "entity_id"
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_BEARER_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_BEARER_TOKEN, null)
    }

    fun clearToken() {
        prefs.edit().remove(KEY_BEARER_TOKEN).apply()
    }

    // Save the entity id header value
    fun saveEntityId(entityId: String) {
        prefs.edit().putString(KEY_ENTITY_ID, entityId).apply()
    }

    // Retrieve the saved entity id, or null if not set
    fun getEntityId(): String? {
        return prefs.getString(KEY_ENTITY_ID, null)
    }

    // Clear both token and entity id
    fun clearAll() {
        prefs.edit().remove(KEY_BEARER_TOKEN).remove(KEY_ENTITY_ID).apply()
    }
}
