package it.unisannio.muses.activities

import android.content.Intent
import android.util.Log
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unisannio.muses.MainActivity
import it.unisannio.muses.R
import it.unisannio.muses.helpers.AuthTokenManager
import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.data.models.LoginBody
import it.unisannio.muses.data.repositories.AuthRepository
import it.unisannio.muses.utils.ThemeManager
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {

    private lateinit var authTokenManager: AuthTokenManager
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        // Inizializza il tema prima di tutto
        ThemeManager.initializeTheme(this)
        
        super.onCreate(savedInstanceState)
        authTokenManager = AuthTokenManager(this)
        // Use the XML layout activity_login.xml and wire the views
        setContentView(R.layout.activity_login)

        usernameEditText = findViewById(R.id.editTextUsername)
        passwordEditText = findViewById(R.id.editTextPassword)
        loginButton = findViewById(R.id.btnLogin)
        registerButton = findViewById(R.id.btnRegister)
        progressBar = findViewById(R.id.progressBar)

        // Initialize repository and ViewModel-like usage
        val viewModel = LoginViewModel(AuthRepository(RetrofitInstance.api))

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            // Basic input validation
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            // Perform login using ViewModel which uses coroutines internally
            viewModel.onLoginClick(
                username = username,
                password = password,
                onSuccess = { token, entityId ->
                    progressBar.visibility = View.GONE
                    authTokenManager.saveToken(token)
                    entityId?.let { authTokenManager.saveEntityId(it) }
                    Log.d("AuthToken", token)
                    Log.d("EntityId", entityId ?: "null")
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                },
                onError = { message ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            )
        }

        registerButton.setOnClickListener {
            // Navigate to RegisterActivity
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    fun onLoginClick(
        username: String,
        password: String,
        onSuccess: (String, String?) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.login(LoginBody(username, password))
            result.onSuccess { loginResult ->
                // Pass both token and optional entityId to caller
                onSuccess(loginResult.tokenResponse.token, loginResult.entityId)
            }.onFailure { e ->
                onError(e.message ?: "Login failed")
            }
        }
    }
}