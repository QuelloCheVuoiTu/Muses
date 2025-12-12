package com.example.museo_gui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.museo_gui.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var sessionManager: SessionManager

    companion object {
        private const val SPLASH_DELAY = 2000L // 2 secondi
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Nascondi la action bar se presente
        supportActionBar?.hide()

        // Dopo il delay, controlla se l'utente è loggato
        Handler(Looper.getMainLooper()).postDelayed({
            if (sessionManager.isLoggedIn()) {
                // Utente già loggato, vai alla MainActivity
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                // Utente non loggato, vai al Login
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, SPLASH_DELAY)
    }
}