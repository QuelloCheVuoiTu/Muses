package it.unisannio.muses.musesadmin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import it.unisannio.muses.musesadmin.activities.LoginActivity
import it.unisannio.muses.musesadmin.activities.QRScanActivity
import it.unisannio.muses.musesadmin.api.RetrofitInstance
import it.unisannio.muses.musesadmin.helpers.AuthTokenManager

class MainActivity : ComponentActivity() {
    private lateinit var authTokenManager: AuthTokenManager
    private lateinit var btnScanQr: Button
    private lateinit var btnLogout: Button
    private lateinit var textWelcome: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize auth token manager
        authTokenManager = AuthTokenManager(this)

        val savedToken = authTokenManager.getToken()
        if (savedToken.isNullOrEmpty()) {
            // No token saved, redirect to login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        Log.d("MainActivity", "Saved token: $savedToken")

        // Configure RetrofitInstance to use the token
        RetrofitInstance.tokenProvider = {
            authTokenManager.getToken()
        }

        // Set the XML layout
        setContentView(R.layout.activity_main)

        // Initialize views
        btnScanQr = findViewById(R.id.btnScanQr)
        btnLogout = findViewById(R.id.btnLogout)
        textWelcome = findViewById(R.id.textWelcome)

        // Set up click listeners
        btnScanQr.setOnClickListener {
            startActivity(Intent(this, QRScanActivity::class.java))
        }

        btnLogout.setOnClickListener {
            // Clear token and go back to login
            authTokenManager.clearAll()
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}