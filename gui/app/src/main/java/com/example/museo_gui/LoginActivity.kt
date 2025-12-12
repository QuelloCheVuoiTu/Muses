package com.example.museo_gui

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONException
import org.json.JSONObject
import com.example.museo_gui.models.User

class LoginActivity : AppCompatActivity() {
    private var editTextUsername: TextInputEditText? = null
    private var editTextPassword: TextInputEditText? = null
    private var btnLogin: Button? = null
    private var btnRegister: Button? = null
    private var progressBar: ProgressBar? = null

    private var requestQueue: RequestQueue? = null

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initializeViews()
        setupClickListeners()

        sessionManager = SessionManager(this)

        // Inizializza la coda delle richieste Volley
        requestQueue = Volley.newRequestQueue(this)
    }

    private fun initializeViews() {
        editTextUsername = findViewById<TextInputEditText?>(R.id.editTextUsername)
        editTextPassword = findViewById<TextInputEditText?>(R.id.editTextPassword)
        btnLogin = findViewById<Button?>(R.id.btnLogin)
        btnRegister = findViewById<Button?>(R.id.btnRegister)
        progressBar = findViewById<ProgressBar?>(R.id.progressBar)
    }

    private fun setupClickListeners() {
        btnLogin!!.setOnClickListener {
            attemptLogin()
        }

        btnRegister!!.setOnClickListener {
            openRegisterActivity()
        }
    }

    private fun attemptLogin() {
        // Reset degli errori
        editTextUsername!!.setError(null)
        editTextPassword!!.setError(null)

        // Ottieni i valori dai campi
        val username = editTextUsername!!.getText().toString().trim { it <= ' ' }
        val password = editTextPassword!!.getText().toString()

        var cancel = false
        var focusView: View? = null

        // Verifica la password
        if (TextUtils.isEmpty(password)) {
            editTextPassword!!.setError("Questo campo è obbligatorio")
            focusView = editTextPassword
            cancel = true
        }

        // Verifica l'username
        if (TextUtils.isEmpty(username)) {
            editTextUsername!!.setError("Questo campo è obbligatorio")
            focusView = editTextUsername
            cancel = true
        }

        if (cancel) {
            // Se ci sono errori, metti il focus sul primo campo con errore
            focusView!!.requestFocus()
        } else {
            // Tutto ok, procedi con il login
            showProgress(true)
            performLogin(username, password)
        }
    }

    private fun performLogin(username: String, password: String) {
        // Crea la richiesta GET per ottenere tutti gli utenti
        val request = JsonObjectRequest(
            Request.Method.GET,
            USERS_URL,
            null,
            Response.Listener<JSONObject> { response ->
                showProgress(false)
                try {
                    // Controlla se la risposta contiene gli utenti
                    if (response.has("users")) {
                        val usersArray = response.getJSONArray("users")
                        var userFound = false

                        // Cerca l'utente nell'array
                        for (i in 0 until usersArray.length()) {
                            val user = usersArray.getJSONObject(i)
                            val userUsername = user.getString("username")
                            val userPassword = user.getString("password")

                            // Verifica se username e password corrispondono
                            if (userUsername == username && userPassword == password) {
                                userFound = true
                                val userId = user.getString("id")
                                val userEmail = user.getString("mail")

                                Log.d(TAG, "Login successful for user: $username")
                                Log.d(TAG, "User data - ID: $userId, Username: $userUsername, Email: $userEmail")

                                // CREA OGGETTO USER
                                val userObj = User(
                                    id = userId,
                                    username = userUsername,
                                    mail = userEmail,
                                    password = userPassword
                                )

                                // SALVA LA SESSIONE
                                Log.d(TAG, "Salvando sessione utente...")
                                sessionManager.saveUserSession(userObj)

                                // VERIFICA CHE SIA STATA SALVATA
                                Log.d(TAG, "Verifica salvataggio - Username: ${sessionManager.getUsername()}")
                                Log.d(TAG, "Verifica salvataggio - Email: ${sessionManager.getEmail()}")
                                Log.d(TAG, "Verifica salvataggio - Logged in: ${sessionManager.isLoggedIn()}")

                                showToast("Login effettuato con successo!")

                                // Naviga alla MainActivity
                                navigateToMainActivity(userId, userUsername, userEmail)
                                break
                            }
                        }

                        if (!userFound) {
                            showToast("Username o password non corretti")
                            Log.d(TAG, "Login failed: invalid credentials")
                        }

                    } else {
                        showToast("Errore nel recupero degli utenti")
                        Log.e(TAG, "Response does not contain users array")
                    }

                } catch (e: JSONException) {
                    Log.e(TAG, "Error parsing JSON response: " + e.message)
                    showToast("Errore nell'elaborazione della risposta del server")
                }
            },
            Response.ErrorListener { error ->
                showProgress(false)
                Log.e(TAG, "Error during login request: " + error.message)

                var errorMessage = "Errore di connessione"
                if (error.networkResponse != null) {
                    when (error.networkResponse.statusCode) {
                        404 -> errorMessage = "Servizio non disponibile"
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

    // 4. MODIFICA anche navigateToMainActivity per non passare i parametri (ora sono nel SessionManager)
    private fun navigateToMainActivity(userId: String, username: String, userEmail: String) {
        val intent = Intent(this, MainActivity::class.java)
        // Non serve più passare i dati come extra, sono già nel SessionManager
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun openRegisterActivity() {
        val intent = Intent(this@LoginActivity, RegisterActivity::class.java)
        startActivity(intent)
    }

    private fun showProgress(show: Boolean) {
        progressBar!!.visibility = if (show) View.VISIBLE else View.GONE
        btnLogin!!.isEnabled = !show
        btnRegister!!.isEnabled = !show
    }

    private fun showToast(message: String?) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        requestQueue?.cancelAll(TAG)
    }

    companion object {
        private const val TAG = "LoginActivity"
        private const val USERS_URL = "http://172.31.0.110:31840/users"
    }
}