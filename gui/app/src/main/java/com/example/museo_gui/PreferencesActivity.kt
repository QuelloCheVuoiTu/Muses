package com.example.museo_gui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import androidx.appcompat.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

class PreferencesActivity : AppCompatActivity() {
    private var recyclerViewMuseumTypes: RecyclerView? = null
    private var recyclerViewArtworkTypes: RecyclerView? = null

    // Le liste originali complete di tutti i tipi (non filtrate)
    private val museumTypes: MutableList<String?> = ArrayList()
    private val artworkTypes: MutableList<String?> = ArrayList()

    // Le liste filtrate che verranno visualizzate dalle RecyclerView
    private val filteredMuseumTypes: MutableList<String?> = ArrayList()
    private val filteredArtworkTypes: MutableList<String?> = ArrayList()

    private var museumTypesAdapter: TypeAdapter? = null
    private var artworkTypesAdapter: TypeAdapter? = null

    private val selectedMuseumTypes: MutableSet<String?> = HashSet()
    private val selectedArtworkTypes: MutableSet<String?> = HashSet()

    private lateinit var sessionManager: SessionManager
    private lateinit var searchEditText: EditText
    private var pendingUserPreferences: List<String>? = null

    // Improved OkHttpClient configuration
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val userPreferencesLoaded = AtomicBoolean(false)
    private val museumTypesLoaded = AtomicBoolean(false)
    private val artworkTypesLoaded = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)

        sessionManager = SessionManager(this)
        initViews()
    }

    override fun onStart() {
        super.onStart()
        // Carica sempre i dati freschi quando l'activity diventa visibile
        fetchAllDataFromServer()
    }

    private fun initViews() {
        recyclerViewMuseumTypes = findViewById(R.id.recycler_museum_types)
        recyclerViewArtworkTypes = findViewById(R.id.recycler_artwork_types)
        searchEditText = findViewById(R.id.edit_text_search_preferences)

        recyclerViewMuseumTypes?.layoutManager = LinearLayoutManager(this)
        recyclerViewArtworkTypes?.layoutManager = LinearLayoutManager(this)

        museumTypesAdapter = TypeAdapter(filteredMuseumTypes, selectedMuseumTypes)
        artworkTypesAdapter = TypeAdapter(filteredArtworkTypes, selectedArtworkTypes)

        recyclerViewMuseumTypes?.adapter = museumTypesAdapter
        recyclerViewArtworkTypes?.adapter = artworkTypesAdapter

        val saveButton = findViewById<Button>(R.id.btn_save_preferences)
        saveButton.setOnClickListener { savePreferencesAndUpload() }

        val resetButton = findViewById<Button>(R.id.btn_reset_preferences)
        resetButton.setOnClickListener { showResetConfirmationDialog() }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterLists(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun fetchAllDataFromServer() {
        // Reset dei flag di caricamento
        userPreferencesLoaded.set(false)
        museumTypesLoaded.set(false)
        artworkTypesLoaded.set(false)

        // Pulisci le selezioni precedenti
        selectedMuseumTypes.clear()
        selectedArtworkTypes.clear()
        pendingUserPreferences = null

        Log.d(TAG, "Starting fetchAllDataFromServer...")

        fetchUserPreferences()
        fetchMuseumTypes()
        fetchArtworkTypes()
    }

    private fun checkAllInitialDataLoaded() {
        if (userPreferencesLoaded.get() && museumTypesLoaded.get() && artworkTypesLoaded.get()) {
            Log.d(TAG, "All initial data loaded. Updating UI...")

            runOnUiThread {
                // Prima popola le liste filtrate con tutti gli elementi
                filteredMuseumTypes.clear()
                filteredMuseumTypes.addAll(museumTypes)
                filteredArtworkTypes.clear()
                filteredArtworkTypes.addAll(artworkTypes)

                // Applica le preferenze pending se esistono
                pendingUserPreferences?.let { preferences ->
                    Log.d(TAG, "Applying ${preferences.size} pending preferences...")
                    applyUserPreferences(preferences)
                    pendingUserPreferences = null
                }

                // Notifica gli adapter DOPO aver applicato le preferenze
                museumTypesAdapter?.notifyDataSetChanged()
                artworkTypesAdapter?.notifyDataSetChanged()

                Log.d(TAG, "Final state - Selected museum types: ${selectedMuseumTypes.size}, Selected artwork types: ${selectedArtworkTypes.size}")
                Toast.makeText(this, "Preferenze caricate!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "Still waiting for data - UserPrefs: ${userPreferencesLoaded.get()}, MuseumTypes: ${museumTypesLoaded.get()}, ArtworkTypes: ${artworkTypesLoaded.get()}")
        }
    }

    private fun filterLists(query: String) {
        val lowerCaseQuery = query.lowercase()

        filteredMuseumTypes.clear()
        filteredArtworkTypes.clear()

        if (lowerCaseQuery.isEmpty()) {
            filteredMuseumTypes.addAll(museumTypes)
            filteredArtworkTypes.addAll(artworkTypes)
        } else {
            museumTypes.forEach { type ->
                if (type?.lowercase()?.contains(lowerCaseQuery) == true) {
                    filteredMuseumTypes.add(type)
                }
            }
            artworkTypes.forEach { type ->
                if (type?.lowercase()?.contains(lowerCaseQuery) == true) {
                    filteredArtworkTypes.add(type)
                }
            }
        }

        museumTypesAdapter?.notifyDataSetChanged()
        artworkTypesAdapter?.notifyDataSetChanged()
    }

    private fun fetchUserPreferences() {
        val userId = sessionManager.getUserId()
        if (userId.isNullOrEmpty()) {
            Log.e(TAG, "User ID is null or empty. Cannot fetch user preferences.")
            userPreferencesLoaded.set(true)
            checkAllInitialDataLoaded()
            return
        }

        val url = "$GET_PREFERENCES_URL/$userId"
        val request: Request = Request.Builder()
            .url(url)
            .build()

        Log.d(TAG, "Fetching user preferences from: $url for user: $userId")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network failure for user preferences: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@PreferencesActivity,
                        "Errore nel caricamento preferenze utente: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                userPreferencesLoaded.set(true)
                checkAllInitialDataLoaded()
            }

            override fun onResponse(call: Call, response: Response) {
                var responseBody: String? = null
                try {
                    Log.d(TAG, "User preferences response code: ${response.code}")

                    if (response.isSuccessful) {
                        // Safe response body reading
                        responseBody = response.body?.use { it.string() }
                        if (!responseBody.isNullOrEmpty()) {
                            Log.d(TAG, "User preferences raw response body: $responseBody")
                            parseUserPreferencesResponse(responseBody)
                        } else {
                            Log.d(TAG, "Empty response body for user preferences")
                            pendingUserPreferences = emptyList()
                        }
                    } else {
                        Log.e(TAG, "Server returned error for user preferences: Code ${response.code}")
                        runOnUiThread {
                            Toast.makeText(
                                this@PreferencesActivity,
                                "Errore server preferenze utente: Codice ${response.code}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception handling user preferences response: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(
                            this@PreferencesActivity,
                            "Errore di connessione per le preferenze utente",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } finally {
                    response.close()
                    userPreferencesLoaded.set(true)
                    checkAllInitialDataLoaded()
                }
            }
        })
    }

    private fun parseUserPreferencesResponse(json: String) {
        try {
            Log.d(TAG, "Parsing user preferences JSON: $json")
            val jsonObject = JSONObject(json)
            val preferencesArray = jsonObject.optJSONArray("preferences")

            val tempPreferences = mutableListOf<String>()

            if (preferencesArray != null) {
                Log.d(TAG, "Found ${preferencesArray.length()} preferences in server response")
                for (i in 0 until preferencesArray.length()) {
                    val preferenceObject = preferencesArray.getJSONObject(i)
                    val preference = preferenceObject.getString("preferenza")
                    tempPreferences.add(preference)
                    Log.d(TAG, "Extracted preference: '$preference'")
                }
            } else {
                Log.d(TAG, "No preferences found in server response")
            }

            pendingUserPreferences = tempPreferences
            Log.d(TAG, "Saved ${tempPreferences.size} preferences as pending")

        } catch (e: JSONException) {
            Log.e(TAG, "JSON parsing error for user preferences: ${e.message}", e)
            pendingUserPreferences = emptyList()
            runOnUiThread {
                Toast.makeText(
                    this@PreferencesActivity,
                    "Errore di formato dati preferenze utente",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun applyUserPreferences(preferences: List<String>) {
        selectedMuseumTypes.clear()
        selectedArtworkTypes.clear()

        Log.d(TAG, "Applying ${preferences.size} user preferences...")
        Log.d(TAG, "Available museum types: ${museumTypes.size}")
        Log.d(TAG, "Available artwork types: ${artworkTypes.size}")

        for (preference in preferences) {
            Log.d(TAG, "Processing preference: '$preference'")

            var matched = false
            if (museumTypes.contains(preference)) {
                selectedMuseumTypes.add(preference)
                matched = true
                Log.d(TAG, "✓ MATCHED museum type: '$preference'")
            }
            if (artworkTypes.contains(preference)) {
                selectedArtworkTypes.add(preference)
                matched = true
                Log.d(TAG, "✓ MATCHED artwork type: '$preference'")
            }

            if (!matched) {
                Log.w(TAG, "✗ NO MATCH for preference: '$preference'")
            }
        }

        Log.d(TAG, "Final selections - Museum: ${selectedMuseumTypes.size}, Artwork: ${selectedArtworkTypes.size}")
        Log.d(TAG, "Selected museum types: $selectedMuseumTypes")
        Log.d(TAG, "Selected artwork types: $selectedArtworkTypes")
    }

    private fun fetchMuseumTypes() {
        val request: Request = Request.Builder()
            .url(MUSEUM_TYPES_URL)
            .build()

        Log.d(TAG, "Fetching museum types from: $MUSEUM_TYPES_URL")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network failure for museum types: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@PreferencesActivity,
                        "Errore di connessione tipi musei: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                museumTypesLoaded.set(true)
                checkAllInitialDataLoaded()
            }

            override fun onResponse(call: Call, response: Response) {
                var responseBody: String? = null
                try {
                    Log.d(TAG, "Museum types response code: ${response.code}")

                    if (response.isSuccessful) {
                        // Safe response body reading
                        responseBody = response.body?.use { it.string() }
                        if (!responseBody.isNullOrEmpty()) {
                            Log.d(TAG, "Museum types raw response body: $responseBody")
                            parseMuseumTypesResponse(responseBody)
                        } else {
                            Log.e(TAG, "Empty response body for museum types")
                        }
                    } else {
                        Log.e(TAG, "Server returned error for museum types: Code ${response.code}")
                        runOnUiThread {
                            Toast.makeText(
                                this@PreferencesActivity,
                                "Errore server tipi musei: Codice ${response.code}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception handling museum types response: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(
                            this@PreferencesActivity,
                            "Errore di connessione per i tipi di musei",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } finally {
                    response.close()
                    museumTypesLoaded.set(true)
                    checkAllInitialDataLoaded()
                }
            }
        })
    }

    private fun parseMuseumTypesResponse(json: String) {
        try {
            Log.d(TAG, "Attempting to parse museum types JSON.")
            val jsonObject = JSONObject(json)
            val typesArray = jsonObject.getJSONArray("types")

            museumTypes.clear()
            for (i in 0 until typesArray.length()) {
                museumTypes.add(typesArray.getString(i))
            }
            Log.d(TAG, "Parsed ${museumTypes.size} museum types.")

            runOnUiThread {
                if (searchEditText.text.toString().isEmpty()) {
                    filteredMuseumTypes.clear()
                    filteredMuseumTypes.addAll(museumTypes)
                }
            }

        } catch (e: JSONException) {
            Log.e(TAG, "JSON parsing error for museum types: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(
                    this@PreferencesActivity,
                    "Errore di formato dati tipi musei",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun fetchArtworkTypes() {
        val request: Request = Request.Builder()
            .url(ARTWORK_TYPES_URL)
            .build()

        Log.d(TAG, "Fetching artwork types from: $ARTWORK_TYPES_URL")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network failure for artwork types: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@PreferencesActivity,
                        "Errore di connessione tipi opere: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                artworkTypesLoaded.set(true)
                checkAllInitialDataLoaded()
            }

            override fun onResponse(call: Call, response: Response) {
                var responseBody: String? = null
                try {
                    Log.d(TAG, "Artwork types response code: ${response.code}")

                    if (response.isSuccessful) {
                        // Safe response body reading with proper resource management
                        responseBody = response.body?.use { it.string() }
                        if (!responseBody.isNullOrEmpty()) {
                            Log.d(TAG, "Artwork types raw response body: $responseBody")
                            parseArtworkTypesResponse(responseBody)
                        } else {
                            Log.e(TAG, "Empty response body for artwork types")
                        }
                    } else {
                        Log.e(TAG, "Server returned error for artwork types: Code ${response.code}")
                        runOnUiThread {
                            Toast.makeText(
                                this@PreferencesActivity,
                                "Errore server tipi opere: Codice ${response.code}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception handling artwork types response: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(
                            this@PreferencesActivity,
                            "Errore di connessione per i tipi di opere",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } finally {
                    response.close()
                    artworkTypesLoaded.set(true)
                    checkAllInitialDataLoaded()
                }
            }
        })
    }

    private fun parseArtworkTypesResponse(json: String) {
        try {
            Log.d(TAG, "Attempting to parse artwork types JSON.")
            val jsonObject = JSONObject(json)
            val dataArray = jsonObject.getJSONArray("data")

            artworkTypes.clear()
            for (i in 0 until dataArray.length()) {
                artworkTypes.add(dataArray.getString(i))
            }
            Log.d(TAG, "Parsed ${artworkTypes.size} artwork types.")

            runOnUiThread {
                if (searchEditText.text.toString().isEmpty()) {
                    filteredArtworkTypes.clear()
                    filteredArtworkTypes.addAll(artworkTypes)
                }
            }

        } catch (e: JSONException) {
            Log.e(TAG, "JSON parsing error for artwork types: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(
                    this@PreferencesActivity,
                    "Errore di formato dati tipi opere",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun savePreferencesAndUpload() {
        savePreferencesLocally()

        val userId = sessionManager.getUserId()
        if (userId.isNullOrEmpty()) {
            Toast.makeText(this, "Errore: ID utente non disponibile. Impossibile salvare preferenze sul server.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "User ID is null or empty. Cannot upload preferences.")
            finish()
            return
        }

        val allSelectedPreferences = mutableListOf<String>()
        selectedMuseumTypes.filterNotNull().forEach { allSelectedPreferences.add(it) }
        selectedArtworkTypes.filterNotNull().forEach { allSelectedPreferences.add(it) }

        if (allSelectedPreferences.isEmpty()) {
            Toast.makeText(this, "Nessuna preferenza selezionata da salvare sul server. Se vuoi eliminare tutte le preferenze, usa il pulsante 'Reset Preferenze'.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Log.d(TAG, "Attempting to upload ${allSelectedPreferences.size} preferences for user: $userId")

        deleteExistingPreferencesThenUploadNew(userId, allSelectedPreferences)
    }

    private fun deleteExistingPreferencesThenUploadNew(userId: String, newPreferences: List<String>) {
        val url = "$DELETE_PREFERENCE_URL/$userId"
        val request: Request = Request.Builder()
            .url(url)
            .delete()
            .build()

        Log.d(TAG, "Attempting to delete existing preferences before uploading new ones for user: $userId")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network failure during old preferences deletion: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@PreferencesActivity,
                        "Errore di rete durante la cancellazione delle vecchie preferenze: ${e.message}. Non è stato possibile caricare le nuove.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                finish()
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful || response.code == 404) {
                        Log.d(TAG, "Successfully deleted existing user preferences or none existed.")
                        uploadNewPreferences(userId, newPreferences)
                    } else {
                        Log.e(TAG, "Server error during old preferences deletion: Code ${response.code}")
                        runOnUiThread {
                            Toast.makeText(
                                this@PreferencesActivity,
                                "Errore server durante la cancellazione delle vecchie preferenze: Codice ${response.code}. Non è stato possibile caricare le nuove.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        finish()
                    }
                } finally {
                    response.close()
                }
            }
        })
    }

    private fun uploadNewPreferences(userId: String, preferencesToUpload: List<String>) {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val successfulUploads = AtomicInteger(0)
        val failedUploads = AtomicInteger(0)
        val totalUploads = preferencesToUpload.size

        if (totalUploads == 0) {
            runOnUiThread {
                Toast.makeText(this@PreferencesActivity, "Preferenze utente aggiornate (nessuna preferenza attiva).", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }

        for (preference in preferencesToUpload) {
            val jsonObject = JSONObject().apply {
                put("iduser", userId)
                put("preferenza", preference)
            }
            val requestBody = jsonObject.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(ADD_PREFERENCE_URL)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    failedUploads.incrementAndGet()
                    Log.e(TAG, "Failed to upload preference '$preference': ${e.message}", e)
                    checkAllUploadsComplete(successfulUploads, failedUploads, totalUploads)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (response.isSuccessful) {
                            successfulUploads.incrementAndGet()
                            Log.d(TAG, "Successfully uploaded preference '$preference'")
                        } else {
                            failedUploads.incrementAndGet()
                            Log.e(TAG, "Failed to upload preference '$preference'. Code: ${response.code}")
                        }
                    } finally {
                        response.close()
                    }
                    checkAllUploadsComplete(successfulUploads, failedUploads, totalUploads)
                }
            })
        }
    }

    private fun checkAllUploadsComplete(successful: AtomicInteger, failed: AtomicInteger, total: Int) {
        val completed = successful.get() + failed.get()
        if (completed == total) {
            runOnUiThread {
                val message = if (failed.get() == 0) {
                    "Preferenze salvate localmente e caricate sul server con successo!"
                } else if (successful.get() == 0) {
                    "Errore: Nessuna preferenza caricata sul server. Controlla la connessione e il server."
                } else {
                    "Preferenze salvate localmente. Caricate ${successful.get()} di $total preferenze sul server. Fallite: ${failed.get()}."
                }
                Toast.makeText(this@PreferencesActivity, message, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun savePreferencesLocally() {
        val prefs = getSharedPreferences("FilterPreferences", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putStringSet("selected_museum_types", selectedMuseumTypes)
        editor.putStringSet("selected_artwork_types", selectedArtworkTypes)
        editor.apply()
        Log.d(TAG, "Preferences saved locally.")
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Conferma Reset")
            .setMessage("Sei sicuro di voler resettare tutte le preferenze? Questa azione eliminerà anche le preferenze salvate sul server.")
            .setPositiveButton("Reset") { dialog, which ->
                performReset()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun performReset() {
        Log.d(TAG, "Resetting preferences...")

        selectedMuseumTypes.clear()
        selectedArtworkTypes.clear()
        runOnUiThread {
            filteredMuseumTypes.clear()
            filteredMuseumTypes.addAll(museumTypes)
            filteredArtworkTypes.clear()
            filteredArtworkTypes.addAll(artworkTypes)

            museumTypesAdapter?.notifyDataSetChanged()
            artworkTypesAdapter?.notifyDataSetChanged()
        }
        Log.d(TAG, "Local selections cleared.")

        val prefs = getSharedPreferences("FilterPreferences", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "Local saved preferences cleared from SharedPreferences.")

        deleteUserPreferencesFromServer()
    }

    private fun deleteUserPreferencesFromServer() {
        val userId = sessionManager.getUserId()
        if (userId.isNullOrEmpty()) {
            Toast.makeText(this, "Errore: ID utente non disponibile. Impossibile resettare preferenze sul server.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "User ID is null or empty. Cannot delete preferences from server.")
            return
        }

        val url = "$DELETE_PREFERENCE_URL/$userId"
        val request: Request = Request.Builder()
            .url(url)
            .delete()
            .build()

        Log.d(TAG, "Attempting to delete preferences from server: $url for user: $userId")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network failure during preferences reset: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@PreferencesActivity,
                        "Errore di rete durante il reset delle preferenze: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful || response.code == 404) {
                        Log.d(TAG, "Successfully deleted user preferences from server.")
                        runOnUiThread {
                            Toast.makeText(this@PreferencesActivity, "Preferenze utente eliminate dal server!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } else {
                        Log.e(TAG, "Server error during preferences reset: Code ${response.code}")
                        runOnUiThread {
                            Toast.makeText(
                                this@PreferencesActivity,
                                "Errore server durante il reset delle preferenze: Codice ${response.code}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } finally {
                    response.close()
                }
            }
        })
    }

    companion object {
        private const val TAG = "PreferencesActivity"
        private const val MUSEUM_TYPES_URL = "http://172.31.0.110:31705/museum/gettypes"
        private const val ARTWORK_TYPES_URL = "http://172.31.0.110:31705/opere/gettypes"
        private const val ADD_PREFERENCE_URL = "http://172.31.0.247:32414/addpreference"
        private const val GET_PREFERENCES_URL = "http://172.31.0.247:32414/getpreference"
        private const val DELETE_PREFERENCE_URL = "http://172.31.0.247:32414/delpreference"
    }
}