package it.unisannio.muses.activities

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.unisannio.muses.R
import it.unisannio.muses.data.repositories.UserRepository
import it.unisannio.muses.data.repositories.MissionRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.net.SocketTimeoutException
import android.widget.TextView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import it.unisannio.muses.utils.ThemeManager

class PreferencesActivity : ComponentActivity() {
    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private var swipeRefresh: SwipeRefreshLayout? = null
    private var inputField: android.widget.EditText? = null
    private var saveButton: android.widget.Button? = null
    private var resetButton: android.widget.Button? = null
    private var genButton: android.widget.Button? = null
    private var isGeneratingMission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Inizializza il tema prima di tutto
        ThemeManager.initializeTheme(this)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)

        recyclerView = findViewById(R.id.preferences_recycler_view)
        progressBar = findViewById(R.id.preferences_progress)
        emptyView = findViewById(R.id.preferences_empty_view)

        recyclerView.layoutManager = LinearLayoutManager(this)

    swipeRefresh = findViewById(R.id.preferences_swipe)
    inputField = findViewById(R.id.preferences_input)
    saveButton = findViewById(R.id.preferences_save_button)
    resetButton = findViewById(R.id.preferences_reset_button)
    genButton = findViewById(R.id.preferences_gen_button)
        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""

        swipeRefresh?.setOnRefreshListener {
            fetchPreferences(userId)
        }

        saveButton?.setOnClickListener {
            val raw = inputField?.text?.toString() ?: ""
            // Split by comma and trim, filter out empty entries
            val prefs = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            sendPreferences(userId, prefs)
        }

        resetButton?.setOnClickListener {
            // send empty preferences list
            sendPreferences(userId, emptyList())
        }

        genButton?.setOnClickListener {
            // Check if we're already generating a mission
            if (isGeneratingMission) {
                android.widget.Toast.makeText(this, "Generazione missione in corso, attendi...", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Disable button and show loading
            isGeneratingMission = true
            genButton?.isEnabled = false
            genButton?.text = "Generando..."
            
            progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                try {
                    // First, clean up preferences to avoid 500 errors
                    cleanupPreferencesBeforeMissionGeneration(userId)
                    
                    // Then generate mission with cleaned preferences
                    val missionRepo = MissionRepository()
                    val response = withTimeout(30_000L) {
                        missionRepo.generateMission(userId)
                    }

                    if (response.isSuccessful) {
                        android.widget.Toast.makeText(this@PreferencesActivity, "Missione generata con successo!", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(this@PreferencesActivity, "Errore generazione missione: ${response.code()}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e("PreferencesActivity", "Timeout generating mission (coroutine)", e)
                    android.widget.Toast.makeText(this@PreferencesActivity, "Timeout richiesta (30s)", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: SocketTimeoutException) {
                    // OkHttp/underlying network timeout
                    Log.e("PreferencesActivity", "Timeout generating mission (network)", e)
                    android.widget.Toast.makeText(this@PreferencesActivity, "Timeout richiesta (30s)", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("PreferencesActivity", "Error generating mission", e)
                    android.widget.Toast.makeText(this@PreferencesActivity, "Errore generazione missione", android.widget.Toast.LENGTH_SHORT).show()
                } finally {
                    // Re-enable button after operation completes (success or failure)
                    progressBar.visibility = View.GONE
                    isGeneratingMission = false
                    genButton?.isEnabled = true
                    genButton?.text = "Genera Missione"
                }
            }
        }

        fetchPreferences(userId)
    }

    private fun fetchPreferences(userId: String) {
        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        swipeRefresh?.isRefreshing = false

        val repo = UserRepository()
        lifecycleScope.launch {
            try {
                val response = repo.getPreferences(userId)
                if (response.isSuccessful) {
                    val body: JsonElement? = response.body()
                    val prefsList: List<String> = when {
                        body == null || body.isJsonNull -> emptyList()
                        body.isJsonArray -> {
                            val arr = body.asJsonArray
                            arr.mapNotNull { element ->
                                when {
                                    element.isJsonNull -> null
                                    element.isJsonPrimitive -> {
                                        val str = element.asString
                                        if (str == "null" || str.isBlank()) null else str
                                    }
                                    else -> {
                                        val str = element.toString()
                                        if (str == "null" || str.isBlank()) null else str
                                    }
                                }
                            }
                        }
                        body.isJsonObject -> {
                            // Try common shapes: { "preferences": [..] } or { "key": "value" }
                            val obj = body.asJsonObject
                            when {
                                obj.has("preferences") && obj.get("preferences").isJsonArray -> {
                                    obj.getAsJsonArray("preferences").mapNotNull { element ->
                                        when {
                                            element.isJsonNull -> null
                                            element.isJsonPrimitive -> {
                                                val str = element.asString
                                                if (str == "null" || str.isBlank()) null else str
                                            }
                                            else -> {
                                                val str = element.toString()
                                                if (str == "null" || str.isBlank()) null else str
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    // Convert object entries to a list of strings (keys or values)
                                    obj.entrySet().mapNotNull { entry ->
                                        val v = entry.value
                                        when {
                                            v.isJsonNull -> null
                                            v.isJsonPrimitive -> {
                                                val str = v.asString
                                                if (str == "null" || str.isBlank()) null else str
                                            }
                                            else -> {
                                                val str = v.toString()
                                                if (str == "null" || str.isBlank()) null else str
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> emptyList()
                    }

                    if (prefsList.isEmpty()) {
                        // Clear any previous adapter and show empty view
                        recyclerView.adapter = null
                        recyclerView.visibility = View.GONE
                        emptyView.visibility = View.VISIBLE
                    } else {
                        emptyView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        recyclerView.adapter = PreferencesAdapter(prefsList)
                    }
                } else {
                    Log.e("PreferencesActivity", "Failed to fetch preferences. Code=${response.code()}")
                    emptyView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e("PreferencesActivity", "Error fetching preferences", e)
                emptyView.visibility = View.VISIBLE
            } finally {
                progressBar.visibility = View.GONE
                swipeRefresh?.isRefreshing = false
            }
        }
    }

    private fun sendPreferences(userId: String, prefs: List<String>) {
        // Allow sending empty list (used by Reset) — construct body even if prefs is empty

        progressBar.visibility = View.VISIBLE
        val repo = it.unisannio.muses.data.repositories.UserRepository()

        val body = com.google.gson.JsonObject()
        val array = com.google.gson.JsonArray()
        prefs.forEach { array.add(it) }
        body.add("preferences", array)

        lifecycleScope.launch {
            try {
                val response = repo.patchPreferences(userId, body)
                if (response.isSuccessful) {
                    // refresh preferences on success
                    fetchPreferences(userId)
                    // if we just reset (empty prefs) clear the input field
                    if (prefs.isEmpty()) {
                        inputField?.setText("")
                    }
                } else {
                    Log.e("PreferencesActivity", "Failed to patch preferences. Code=${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("PreferencesActivity", "Error while patching preferences", e)
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun cleanupPreferencesBeforeMissionGeneration(userId: String) {
        try {
            Log.d("PreferencesActivity", "Cleaning up preferences before mission generation")
            val repo = UserRepository()
            
            // Get current preferences
            val response = repo.getPreferences(userId)
            if (response.isSuccessful) {
                val body: JsonElement? = response.body()
                val prefsList: List<String> = when {
                    body == null || body.isJsonNull -> emptyList()
                    body.isJsonArray -> {
                        val arr = body.asJsonArray
                        arr.mapNotNull { element ->
                            when {
                                element.isJsonNull -> null
                                element.isJsonPrimitive -> {
                                    val str = element.asString
                                    if (str == "null" || str.isBlank()) null else str
                                }
                                else -> {
                                    val str = element.toString()
                                    if (str == "null" || str.isBlank()) null else str
                                }
                            }
                        }
                    }
                    body.isJsonObject -> {
                        // Try common shapes: { "preferences": [..] } or { "key": "value" }
                        val obj = body.asJsonObject
                        when {
                            obj.has("preferences") && obj.get("preferences").isJsonArray -> {
                                obj.getAsJsonArray("preferences").mapNotNull { element ->
                                    when {
                                        element.isJsonNull -> null
                                        element.isJsonPrimitive -> {
                                            val str = element.asString
                                            if (str == "null" || str.isBlank()) null else str
                                        }
                                        else -> {
                                            val str = element.toString()
                                            if (str == "null" || str.isBlank()) null else str
                                        }
                                    }
                                }
                            }
                            else -> {
                                // Convert object entries to a list of strings (keys or values)
                                obj.entrySet().mapNotNull { entry ->
                                    val v = entry.value
                                    when {
                                        v.isJsonNull -> null
                                        v.isJsonPrimitive -> {
                                            val str = v.asString
                                            if (str == "null" || str.isBlank()) null else str
                                        }
                                        else -> {
                                            val str = v.toString()
                                            if (str == "null" || str.isBlank()) null else str
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> emptyList()
                }
                
                // If preferences contain null values or are dirty, clean them up
                val originalSize = when {
                    body == null || body.isJsonNull -> 0
                    body.isJsonArray -> body.asJsonArray.size()
                    body.isJsonObject -> {
                        val obj = body.asJsonObject
                        if (obj.has("preferences") && obj.get("preferences").isJsonArray) {
                            obj.getAsJsonArray("preferences").size()
                        } else {
                            obj.entrySet().size
                        }
                    }
                    else -> 0
                }
                
                if (prefsList.size != originalSize) {
                    Log.d("PreferencesActivity", "Cleaning preferences: ${originalSize} -> ${prefsList.size}")
                    // Send cleaned preferences to backend
                    sendPreferences(userId, prefsList)
                } else {
                    Log.d("PreferencesActivity", "Preferences are already clean, no action needed")
                }
            }
        } catch (e: Exception) {
            Log.w("PreferencesActivity", "Failed to cleanup preferences, proceeding anyway", e)
            // Don't throw - proceed with mission generation even if cleanup fails
        }
    }
}
