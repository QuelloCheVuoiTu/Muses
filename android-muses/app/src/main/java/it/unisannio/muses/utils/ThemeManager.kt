package it.unisannio.muses.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Manager per la gestione del tema dell'applicazione
 * Il tema segue automaticamente le impostazioni del sistema
 */
object ThemeManager {
    
    /**
     * Inizializza il tema all'avvio dell'app
     * Il tema segue sempre le impostazioni del sistema (chiaro/scuro)
     */
    fun initializeTheme(context: Context) {
        // Il parametro context è mantenuto per compatibilità con le chiamate esistenti
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
    
    /**
     * Verifica se il tema scuro è attualmente attivo
     */
    fun isDarkThemeActive(context: Context): Boolean {
        val currentNightMode = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}