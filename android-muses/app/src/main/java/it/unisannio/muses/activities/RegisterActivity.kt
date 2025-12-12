package it.unisannio.muses.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unisannio.muses.MainActivity
import it.unisannio.muses.R
import it.unisannio.muses.data.models.LoginBody
import it.unisannio.muses.data.repositories.AuthRepository
import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.helpers.AuthTokenManager
import it.unisannio.muses.utils.ThemeManager
import kotlinx.coroutines.launch

class RegisterActivity : ComponentActivity() {

	private lateinit var authTokenManager: AuthTokenManager
	private lateinit var usernameEditText: EditText
	private lateinit var passwordEditText: EditText
	private lateinit var registerButton: Button
	private lateinit var progressBar: ProgressBar

	override fun onCreate(savedInstanceState: Bundle?) {
		// Inizializza il tema prima di tutto
		ThemeManager.initializeTheme(this)
		
		super.onCreate(savedInstanceState)

		authTokenManager = AuthTokenManager(this)
		setContentView(R.layout.activity_register)

		usernameEditText = findViewById(R.id.editTextUsername)
		passwordEditText = findViewById(R.id.editTextPassword)
		registerButton = findViewById(R.id.btnLogin)
		progressBar = findViewById(R.id.progressBar)

		val viewModel = RegisterViewModel(AuthRepository(RetrofitInstance.api))

		registerButton.setOnClickListener {
			val username = usernameEditText.text.toString()
			val password = passwordEditText.text.toString()

			if (username.isBlank() || password.isBlank()) {
				Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
				return@setOnClickListener
			}

			progressBar.visibility = View.VISIBLE

			viewModel.onRegisterClick(
				username = username,
				password = password,
				onSuccess = { token, entityId ->
					progressBar.visibility = View.GONE
					authTokenManager.saveToken(token)
					entityId?.let { authTokenManager.saveEntityId(it) }
					// Wire Retrofit tokenProvider so subsequent requests include Authorization header
					RetrofitInstance.tokenProvider = { authTokenManager.getToken() }
					Log.d("AuthToken", token)
					Log.d("EntityId", entityId ?: "null")
					Toast.makeText(this, "Registration Successful! Continue profile setup.", Toast.LENGTH_SHORT).show()
					// Navigate to RegisterUserActivity to complete user profile
					// Pass credentials for automatic login attempt after profile completion
					val intent = Intent(this, RegisterUserActivity::class.java)
					intent.putExtra("username", username)
					intent.putExtra("password", password)
					startActivity(intent)
					finish()
				},
				onError = { message ->
					progressBar.visibility = View.GONE
					Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
				}
			)
		}
	}
}

class RegisterViewModel(
	private val authRepository: AuthRepository
) : ViewModel() {

	fun onRegisterClick(
		username: String,
		password: String,
		onSuccess: (String, String?) -> Unit,
		onError: (String) -> Unit
	) {
		viewModelScope.launch {
			val result = authRepository.register(LoginBody(username, password))
			result.onSuccess { loginResult ->
				onSuccess(loginResult.tokenResponse.token, loginResult.entityId)
			}.onFailure { e ->
				onError(e.message ?: "Registration failed")
			}
		}
	}
}