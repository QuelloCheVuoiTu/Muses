package com.example.museo_gui

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONException
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {
    private var editTextUsername: TextInputEditText? = null
    private var editTextEmail: TextInputEditText? = null
    private var editTextPassword: TextInputEditText? = null
    private var editTextConfirmPassword: TextInputEditText? = null
    private var btnRegister: Button? = null
    private var btnBackToLogin: Button? = null
    private var progressBar: ProgressBar? = null

    private var requestQueue: RequestQueue? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        initializeViews()
        setupClickListeners()

        // Inizializza la coda delle richieste Volley
        requestQueue = Volley.newRequestQueue(this)
    }

    private fun initializeViews() {
        editTextUsername = findViewById<TextInputEditText?>(R.id.editTextUsername)
        editTextEmail = findViewById<TextInputEditText?>(R.id.editTextEmail)
        editTextPassword = findViewById<TextInputEditText?>(R.id.editTextPassword)
        editTextConfirmPassword = findViewById<TextInputEditText?>(R.id.editTextConfirmPassword)
        btnRegister = findViewById<Button?>(R.id.btnRegister)
        btnBackToLogin = findViewById<Button?>(R.id.btnBackToLogin)
        progressBar = findViewById<ProgressBar?>(R.id.progressBar)
    }

    private fun setupClickListeners() {
        btnRegister!!.setOnClickListener {
            attemptRegister()
        }

        btnBackToLogin!!.setOnClickListener {
            finish() // Torna alla LoginActivity
        }
    }

    private fun attemptRegister() {
        // Reset degli errori
        editTextUsername!!.setError(null)
        editTextEmail!!.setError(null)
        editTextPassword!!.setError(null)
        editTextConfirmPassword!!.setError(null)

        // Ottieni i valori dai campi
        val username = editTextUsername!!.getText().toString().trim { it <= ' ' }
        val email = editTextEmail!!.getText().toString().trim { it <= ' ' }
        val password = editTextPassword!!.getText().toString()
        val confirmPassword = editTextConfirmPassword!!.getText().toString()

        var cancel = false
        var focusView: View? = null

        // Verifica la conferma password
        if (password != confirmPassword) {
            editTextConfirmPassword!!.setError("Le password non corrispondono")
            focusView = editTextConfirmPassword
            cancel = true
        }

        // Verifica la password
        if (TextUtils.isEmpty(password)) {
            editTextPassword!!.setError("Questo campo è obbligatorio")
            focusView = editTextPassword
            cancel = true
        } else if (password.length < 4) {
            editTextPassword!!.setError("La password deve contenere almeno 4 caratteri")
            focusView = editTextPassword
            cancel = true
        }

        // Verifica l'email
        if (TextUtils.isEmpty(email)) {
            editTextEmail!!.setError("Questo campo è obbligatorio")
            focusView = editTextEmail
            cancel = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail!!.setError("Inserisci un indirizzo email valido")
            focusView = editTextEmail
            cancel = true
        }

        // Verifica l'username
        if (TextUtils.isEmpty(username)) {
            editTextUsername!!.setError("Questo campo è obbligatorio")
            focusView = editTextUsername
            cancel = true
        } else if (username.length < 3) {
            editTextUsername!!.setError("L'username deve contenere almeno 3 caratteri")
            focusView = editTextUsername
            cancel = true
        }

        if (cancel) {
            // Se ci sono errori, metti il focus sul primo campo con errore
            focusView!!.requestFocus()
        } else {
            // Tutto ok, procedi con la registrazione
            showProgress(true)
            performRegister(username, email, password)
        }
    }

    private fun performRegister(username: String?, email: String?, password: String?) {
        // Crea il JSON object per la richiesta
        val jsonBody = JSONObject()
        try {
            jsonBody.put("username", username)
            jsonBody.put("password", password)
            jsonBody.put("mail", email)
        } catch (e: JSONException) {
            Log.e(TAG, "Errore nella creazione del JSON: " + e.message)
            showProgress(false)
            showToast("Errore nella preparazione dei dati")
            return
        }

        // Crea la richiesta POST
        val request = JsonObjectRequest(
            Request.Method.POST,
            REGISTER_URL,
            jsonBody,
            { response ->
                showProgress(false)
                Log.d(TAG, "Registrazione completata: " + response.toString())
                showToast("Registrazione completata con successo!")

                // Torna alla LoginActivity
                finish()
            },
            { error ->
                showProgress(false)
                Log.e(TAG, "Errore nella registrazione: " + error.message)

                var errorMessage = "Errore nella registrazione"
                if (error.networkResponse != null) {
                    when (error.networkResponse.statusCode) {
                        400 -> errorMessage = "Dati non validi"
                        409 -> errorMessage = "Username o email già esistenti"
                        500 -> errorMessage = "Errore del server"
                        else -> errorMessage = "Errore di rete"
                    }
                }

                showToast(errorMessage)
            }
        )

        // Aggiungi la richiesta alla coda
        requestQueue?.add(request)
    }

    private fun showProgress(show: Boolean) {
        progressBar!!.visibility = if (show) View.VISIBLE else View.GONE
        btnRegister!!.isEnabled = !show
        btnBackToLogin!!.isEnabled = !show
    }

    private fun showToast(message: String?) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        requestQueue?.cancelAll(TAG)
    }

    companion object {
        private const val TAG = "RegisterActivity"
        private const val REGISTER_URL = "http://172.31.0.110:31840/users"
    }
}