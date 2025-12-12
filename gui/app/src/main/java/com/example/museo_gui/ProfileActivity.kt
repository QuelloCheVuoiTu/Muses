package com.example.museo_gui

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.museo_gui.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var sessionManager: SessionManager

    companion object {
        private const val TAG = "ProfileActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inizializza SessionManager
        sessionManager = SessionManager(this)

        setupToolbar()
        loadUserData()
        setupClickListeners()

        // DEBUG: Verifica cosa c'è nel SessionManager
        debugSessionData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Profilo"
        }
    }

    private fun loadUserData() {
        // Carica i dati dell'utente
        val username = sessionManager.getUsername() ?: "Nome utente non disponibile"
        val email = sessionManager.getEmail() ?: "Email non disponibile"

        Log.d(TAG, "Username caricato: $username")
        Log.d(TAG, "Email caricata: $email")

        // Aggiorna le view
        binding.tvUsername.text = username
        binding.tvEmail.text = email
        binding.tvPassword.text = "••••••••" // Password nascosta
    }

    private fun setupClickListeners() {
        // Click listener per mostrare/nascondere password
        binding.ivPasswordToggle.setOnClickListener {
            togglePasswordVisibility()
        }
    }

    private fun togglePasswordVisibility() {
        if (binding.tvPassword.text == "••••••••") {
            // Mostra password (se disponibile)
            val password = sessionManager.getPassword()

            Log.d(TAG, "Password recuperata dal SessionManager: '$password'")
            Log.d(TAG, "Password è null? ${password == null}")
            Log.d(TAG, "Password è vuota? ${password.isNullOrEmpty()}")

            if (password.isNullOrEmpty()) {
                binding.tvPassword.text = "Password non disponibile"
                Log.w(TAG, "Password non disponibile nel SessionManager!")
            } else {
                binding.tvPassword.text = password
                Log.d(TAG, "Password mostrata: $password")
            }

            binding.ivPasswordToggle.setImageResource(android.R.drawable.ic_secure)
        } else {
            // Nascondi password
            binding.tvPassword.text = "••••••••"
            binding.ivPasswordToggle.setImageResource(android.R.drawable.ic_partial_secure)
            Log.d(TAG, "Password nascosta")
        }
    }

    private fun debugSessionData() {
        Log.d(TAG, "=== DEBUG SESSION DATA ===")
        Log.d(TAG, "Is logged in: ${sessionManager.isLoggedIn()}")
        Log.d(TAG, "Username: '${sessionManager.getUsername()}'")
        Log.d(TAG, "Email: '${sessionManager.getEmail()}'")
        Log.d(TAG, "Password: '${sessionManager.getPassword()}'")
        Log.d(TAG, "User ID: '${sessionManager.getUserId()}'")

        // Verifica diretta delle SharedPreferences
        val sharedPrefs = getSharedPreferences("user_session", MODE_PRIVATE)
        val allData = sharedPrefs.all

        Log.d(TAG, "=== SHARED PREFERENCES DATA ===")
        for ((key, value) in allData) {
            Log.d(TAG, "$key: '$value'")
        }
        Log.d(TAG, "============================")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}