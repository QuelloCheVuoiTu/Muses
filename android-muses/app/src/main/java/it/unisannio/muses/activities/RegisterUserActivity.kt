package it.unisannio.muses.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unisannio.muses.MainActivity
import it.unisannio.muses.R
import it.unisannio.muses.data.models.CreateUserRequest
import it.unisannio.muses.data.models.LoginBody
import it.unisannio.muses.data.repositories.UserRepository
import it.unisannio.muses.data.repositories.AuthRepository
import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.helpers.AuthTokenManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RegisterUserActivity : ComponentActivity() {

	private val userRepository = UserRepository()
	private lateinit var authTokenManager: AuthTokenManager
	private var registrationUsername: String? = null
	private var registrationPassword: String? = null

	private lateinit var etFirstname: TextInputEditText
	private lateinit var etLastname: TextInputEditText
	private lateinit var etUsername: TextInputEditText
	private lateinit var etEmail: TextInputEditText
	private lateinit var etBirthday: TextInputEditText
	private lateinit var etCountry: TextInputEditText
	private lateinit var btnSubmit: Button
	private lateinit var progressBar: ProgressBar

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_register_user)

		authTokenManager = AuthTokenManager(this)
		
		// Get credentials passed from RegisterActivity (for potential fallback only)
		registrationUsername = intent.getStringExtra("username")
		registrationPassword = intent.getStringExtra("password")

		// Ensure RetrofitInstance is configured to use the existing token
		RetrofitInstance.tokenProvider = { authTokenManager.getToken() }

		etFirstname = findViewById(R.id.et_firstname)
		etLastname = findViewById(R.id.et_lastname)
		etUsername = findViewById(R.id.et_username)
		etEmail = findViewById(R.id.et_email)
		etBirthday = findViewById(R.id.et_birthday)
		etCountry = findViewById(R.id.et_country)
		btnSubmit = findViewById(R.id.btn_submit)
		progressBar = findViewById(R.id.progressBarUser)

		// Show DatePicker when birthday field is clicked (outside submit handler)
		etBirthday.setOnClickListener {
			// Default to 20 years ago
			val cal = java.util.Calendar.getInstance()
			cal.add(java.util.Calendar.YEAR, -20)
			val year = cal.get(java.util.Calendar.YEAR)
			val month = cal.get(java.util.Calendar.MONTH)
			val day = cal.get(java.util.Calendar.DAY_OF_MONTH)

			val dpd = android.app.DatePickerDialog(this, { _, y, m, d ->
				// month is 0-based
				val mm = m + 1
				val dayStr = if (d < 10) "0$d" else "$d"
				val monStr = if (mm < 10) "0$mm" else "$mm"
				etBirthday.setText("$dayStr/$monStr/$y")
			}, year, month, day)

			dpd.show()
		}

		// Submit handler: process form and send request
		btnSubmit.setOnClickListener {
			val firstname = etFirstname.text?.toString()?.trim() ?: ""
			val lastname = etLastname.text?.toString()?.trim() ?: ""
			val username = etUsername.text?.toString()?.trim() ?: ""
			val email = etEmail.text?.toString()?.trim() ?: ""
			val birthdayStr = etBirthday.text?.toString()?.trim() ?: ""
			val country = etCountry.text?.toString()?.trim() ?: ""

			if (firstname.isBlank() || lastname.isBlank() || username.isBlank() || email.isBlank() || birthdayStr.isBlank() || country.isBlank()) {
				Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
				return@setOnClickListener
			}

			// Parse display date (dd/MM/yyyy) and create ISO 8601 string directly
			val sdfLocal = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
			val localDate = try {
				sdfLocal.parse(birthdayStr)
			} catch (e: Exception) {
				null
			}

			if (localDate == null) {
				Toast.makeText(this, "Please pick a valid birthday", Toast.LENGTH_SHORT).show()
				return@setOnClickListener
			}

			// Extract day, month, year from local date and create ISO string directly
			val calendar = Calendar.getInstance()
			calendar.time = localDate
			val year = calendar.get(Calendar.YEAR)
			val month = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
			val day = calendar.get(Calendar.DAY_OF_MONTH)
			
			// Create ISO string directly without timezone conversion
			val birthdayIso = String.format("%04d-%02d-%02dT00:00:00Z", year, month, day)

			val createReq = CreateUserRequest(
				firstname = firstname,
				lastname = lastname,
				username = username,
				email = email,
				birthday = birthdayIso,
				country = country,
				avatarUrl = null,
				preferences = null,
				rangePreferences = null
			)

			progressBar.visibility = View.VISIBLE
			Log.d("CreateUser", "Starting user profile creation")
			Log.d("CreateUser", "Username: $username")
			Log.d("CreateUser", "Current token: ${authTokenManager.getToken()}")
			Log.d("CreateUser", "Current entityId: ${authTokenManager.getEntityId()}")
			
			lifecycleScope.launch {
				try {
					val response = userRepository.createUser(createReq)
					progressBar.visibility = View.GONE
					Log.d("CreateUser", "CreateUser response code: ${response.code()}")
					if (response.isSuccessful) {
						Log.d("CreateUser", "Profile created successfully, starting token refresh")
						Toast.makeText(this@RegisterUserActivity, "Profile created successfully!", Toast.LENGTH_SHORT).show()
						// Profile created successfully, now refresh token to get correct entityId
						refreshTokenForCorrectEntityId()
					} else {
						val msg = response.errorBody()?.string() ?: "Failed to create user"
						Toast.makeText(this@RegisterUserActivity, msg, Toast.LENGTH_LONG).show()
						// If profile creation fails, it might be due to token issues
						// Offer fallback to manual login
						if (msg.contains("unauthorized", ignoreCase = true) || msg.contains("forbidden", ignoreCase = true)) {
							Toast.makeText(this@RegisterUserActivity, "Please login manually to continue.", Toast.LENGTH_LONG).show()
							navigateToLogin()
						}
					}
				} catch (e: Exception) {
					progressBar.visibility = View.GONE
					Toast.makeText(this@RegisterUserActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
					// On any exception, provide fallback option
					if (e.message?.contains("unauthorized", ignoreCase = true) == true) {
						navigateToLogin()
					}
				}
			}
		}
	}

	private fun refreshTokenForCorrectEntityId() {
		if (registrationUsername == null || registrationPassword == null) {
			// If credentials are missing, navigate to MainActivity anyway
			// The user profile creation was successful
			progressBar.visibility = View.GONE
			Toast.makeText(this, "Registration completed successfully!", Toast.LENGTH_SHORT).show()
			startActivity(Intent(this, MainActivity::class.java))
			finish()
			return
		}

		// Clear existing tokens to ensure fresh login
		authTokenManager.clearAll()
		RetrofitInstance.tokenProvider = { null }
		
		val authRepository = AuthRepository(RetrofitInstance.api)
		val refreshViewModel = TokenRefreshViewModel(authRepository)

		Log.d("TokenRefresh", "Starting fresh login after profile creation")
		
		// Add small delay to allow backend to process the new user
		lifecycleScope.launch {
			delay(1000) // Wait 1 second
			
			refreshViewModel.refreshToken(
				username = registrationUsername!!,
				password = registrationPassword!!,
				onSuccess = { token, entityId ->
					progressBar.visibility = View.GONE
					// Save the fresh token and entityId
					authTokenManager.saveToken(token)
					entityId?.let { authTokenManager.saveEntityId(it) }
					// Update Retrofit tokenProvider for subsequent requests
					RetrofitInstance.tokenProvider = { authTokenManager.getToken() }
					Log.d("TokenRefresh", "Fresh login successful after profile creation")
					Log.d("TokenRefresh", "New Token: $token")
					Log.d("TokenRefresh", "New EntityId: ${entityId ?: "null"}")
					Toast.makeText(this@RegisterUserActivity, "Registration completed successfully!", Toast.LENGTH_SHORT).show()
					startActivity(Intent(this@RegisterUserActivity, MainActivity::class.java))
					finish()
				},
				onError = { message ->
					progressBar.visibility = View.GONE
					Log.e("TokenRefresh", "Fresh login failed: $message")
					// If fresh login fails, redirect to manual login
					Toast.makeText(this@RegisterUserActivity, "Registration completed. Please login manually to continue.", Toast.LENGTH_LONG).show()
					navigateToLogin()
				}
			)
		}
	}

	private fun navigateToLogin() {
		// Clear any existing tokens that might be causing authorization issues
		authTokenManager.clearAll()
		// Reset tokenProvider to prevent interference with fresh login
		RetrofitInstance.tokenProvider = { null }
		
		startActivity(Intent(this, LoginActivity::class.java))
		finish()
	}
}

// ViewModel for token refresh after profile creation
class TokenRefreshViewModel(
	private val authRepository: AuthRepository
) : ViewModel() {

	fun refreshToken(
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
				onError(e.message ?: "Token refresh failed")
			}
		}
	}
}