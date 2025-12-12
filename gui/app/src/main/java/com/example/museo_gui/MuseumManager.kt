package com.example.museo_gui

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import com.example.museo_gui.models.*

class MuseumManager {

    private var allMuseums = listOf<MuseumResponse>()
    private var mainMuseums = listOf<MuseumResponse>()
    private var minorMuseums = listOf<MuseumResponse>()

    interface MuseumCallback {
        fun onMuseumsLoaded(museums: List<MuseumResponse>)
        fun onError(error: String)
    }

    fun getMinorMuseumsByParent(parentMuseumName: String): List<MuseumResponse> {
        return minorMuseums.filter { it.parent == parentMuseumName }
    }

    suspend fun loadMuseums(callback: MuseumCallback) {
        try {
            Log.d("Museums", "Caricamento musei...")

            val responseBody = withContext(Dispatchers.IO) {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url("http://172.31.0.110:31705/museum/getmuseums")
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.e("Museums", "Errore HTTP: ${response.code}")
                    null
                }
            }

            if (responseBody != null) {
                Log.d("Museums", "Risposta raw dal server: $responseBody")
                val museums = parseMuseumsResponse(responseBody)

                if (museums.isNotEmpty()) {
                    allMuseums = museums
                    mainMuseums = museums.filter { it.parent == null }
                    minorMuseums = museums.filter { it.parent != null }
                    Log.d("Museums", "Musei caricati: ${museums.size}")

                    callback.onMuseumsLoaded(museums)
                } else {
                    callback.onError("Nessun museo trovato")
                }
            } else {
                callback.onError("Errore nel caricamento dei dati")
            }

        } catch (e: Exception) {
            Log.e("Museums", "Errore nel caricamento musei", e)
            callback.onError("Errore: ${e.message}")
        }
    }

    private fun parseMuseumsResponse(jsonString: String): List<MuseumResponse> {
        return try {
            val gson = Gson()

            // Prova prima come array
            if (jsonString.trim().startsWith("[")) {
                val type = object : TypeToken<List<MuseumResponse>>() {}.type
                gson.fromJson(jsonString, type)
            } else {
                // Prova come oggetto con diversi possibili campi
                val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)

                when {
                    jsonObject.has("museums") -> {
                        val type = object : TypeToken<List<MuseumResponse>>() {}.type
                        gson.fromJson(jsonObject.get("museums"), type)
                    }
                    jsonObject.has("data") -> {
                        val type = object : TypeToken<List<MuseumResponse>>() {}.type
                        gson.fromJson(jsonObject.get("data"), type)
                    }
                    jsonObject.has("results") -> {
                        val type = object : TypeToken<List<MuseumResponse>>() {}.type
                        gson.fromJson(jsonObject.get("results"), type)
                    }
                    else -> {
                        Log.e("Museums", "Struttura JSON non riconosciuta: $jsonString")
                        emptyList()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Museums", "Errore nel parsing JSON", e)
            emptyList()
        }
    }
}