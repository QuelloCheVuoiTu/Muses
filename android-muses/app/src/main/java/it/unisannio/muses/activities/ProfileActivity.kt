package it.unisannio.muses.activities

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.SeekBar
import android.app.DatePickerDialog
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import it.unisannio.muses.R
import it.unisannio.muses.data.models.User
import it.unisannio.muses.data.models.UpdateUserRequest
import it.unisannio.muses.data.repositories.UserRepository
import it.unisannio.muses.helpers.AuthTokenManager
import it.unisannio.muses.utils.ThemeManager
import android.util.Log
import kotlinx.coroutines.launch
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Activity that displays the profile information for a user.
 * Expected layout: `activity_profile.xml` (already present in resources).
 *
 * Behaviour:
 * - Reads an optional intent extra `USER_ID` (String). If absent, tries to use
 *   the saved entity id from `AuthTokenManager`.
 * - Requests user details from the API and fills the views: username, email,
 *   and a masked password placeholder.
 * - Does NOT display `id`, `preferences` or `avatar_url` as requested.
 */
class ProfileActivity : AppCompatActivity() {

	companion object {
		const val EXTRA_USER_ID = "USER_ID"
	}

	private lateinit var tvUsername: TextView
	private lateinit var tvEmail: TextView
	private lateinit var ivProfilePicture: ImageView
	private lateinit var tvFullname: TextView
	private lateinit var tvBirthday: TextView
	private lateinit var tvCountry: TextView
	private lateinit var tvRangePreferences: TextView

	// EditText views for editing mode
	private lateinit var etUsername: android.widget.EditText
	private lateinit var etEmail: android.widget.EditText
	private lateinit var etFullname: android.widget.EditText
	private lateinit var etCountry: android.widget.EditText

	// Special controls for birthday and range
	private lateinit var btnSelectDate: android.widget.Button
	private lateinit var layoutRangeSlider: android.widget.LinearLayout
	private lateinit var seekbarRange: android.widget.SeekBar
	private lateinit var tvRangeDisplay: TextView
	
	private var selectedBirthday: String = ""
	private var selectedRange: Float = 1.0f

	// Buttons
	private lateinit var btnModifyProfile: android.widget.Button
	private lateinit var layoutEditButtons: android.widget.LinearLayout
	private lateinit var btnSaveProfile: android.widget.Button
	private lateinit var btnCancelProfile: android.widget.Button

	private var isEditMode = false
	private var currentUserId: String? = null
	private val userRepository = UserRepository()

	override fun onCreate(savedInstanceState: Bundle?) {
		// Inizializza il tema prima di tutto
		ThemeManager.initializeTheme(this)
		
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_profile)

		// Toolbar setup (if present in layout)
		val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
		setSupportActionBar(toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		// Bind TextViews
        tvUsername = findViewById(R.id.tv_username)
        tvEmail = findViewById(R.id.tv_email)
        ivProfilePicture = findViewById(R.id.iv_profile_picture)
		tvFullname = findViewById(R.id.tv_fullname)
		tvBirthday = findViewById(R.id.tv_birthday)
		tvCountry = findViewById(R.id.tv_country)
		tvRangePreferences = findViewById(R.id.tv_range_preferences)
		
		// Bind EditTexts
		etUsername = findViewById(R.id.et_username)
		etEmail = findViewById(R.id.et_email)
		etFullname = findViewById(R.id.et_fullname)
		etCountry = findViewById(R.id.et_country)
		
		// Bind special controls
		btnSelectDate = findViewById(R.id.btn_select_date)
		layoutRangeSlider = findViewById(R.id.layout_range_slider)
		seekbarRange = findViewById(R.id.seekbar_range)
		tvRangeDisplay = findViewById(R.id.tv_range_display)
		
		// Bind Buttons
		btnModifyProfile = findViewById(R.id.btn_modify_profile)
		layoutEditButtons = findViewById(R.id.layout_edit_buttons)
		btnSaveProfile = findViewById(R.id.btn_save_profile)
		btnCancelProfile = findViewById(R.id.btn_cancel_profile)
		
		// Set up button click listeners
		setupButtonClickListeners()

        // Initially show loading placeholders
        tvUsername.text = getString(R.string.loading)
        tvEmail.text = getString(R.string.loading)

		// Determine which user id to fetch
		val intentUserId = intent.getStringExtra(EXTRA_USER_ID)
		val authTokenManager = AuthTokenManager(this)
		val savedEntityId = authTokenManager.getEntityId()
//		val savedEntityId = "68b6c5fa5e7fd7af3fc17c55"

		val userIdToFetch = intentUserId ?: savedEntityId

		if (userIdToFetch.isNullOrEmpty()) {
			Toast.makeText(this, "No user id available", Toast.LENGTH_LONG).show()
			// Show empty state and return
			tvUsername.text = getString(R.string.unknown)
			tvEmail.text = getString(R.string.unknown)
			return
		}

		// Store the user ID for later use in API calls
		currentUserId = userIdToFetch

		// Fetch user data from API
		lifecycleScope.launch {
			try {
				val response: Response<User> = userRepository.getUser(userIdToFetch)
				if (response.isSuccessful) {
					val user = response.body()
					Log.d("ProfileActivity", "getUser response code=${response.code()} body=${response.body()}")
					if (user != null) {
						Log.d("ProfileActivity", "User fields: id=${user.id} username='${user.username}' email='${user.email}' firstname='${user.firstname}' lastname='${user.lastname}' birthday='${user.birthday}' country='${user.country}' preferences=${user.preferences} range=${user.rangePreferences}")
						populateUser(user)
					} else {
						showError("Empty user response")
					}
				} else {
					val message = "Failed to retrieve user. Code: ${response.code()}"
					showError(message)
				}
			} catch (e: Exception) {
				showError("Error fetching user: ${e.message}")
			}
		}
	}

	private fun populateUser(user: User) {
		// Display a human friendly name when possible
		// Username should show the actual username field
		tvUsername.text = user.username.takeIf { it.isNotBlank() } ?: getString(R.string.unknown)
		tvEmail.text = user.email.takeIf { it.isNotBlank() } ?: getString(R.string.unknown)


		// As requested, do NOT display id, preferences, or avatar_url.
		// Keep the profile picture placeholder as-is.

		// Full name
		val fullname = listOf(user.firstname, user.lastname).filter { it.isNotBlank() }.joinToString(" ")
		tvFullname.text = if (fullname.isNotBlank()) fullname else getString(R.string.unknown)

		// Birthday formatting
		try {
			val sdf = java.text.SimpleDateFormat("dd/MM/yyyy")
			val bday = user.birthday
			if (bday != null) {
				val formatted = sdf.format(bday)
				tvBirthday.text = formatted
			} else {
				tvBirthday.text = getString(R.string.unknown)
			}
		} catch (e: Exception) {
			tvBirthday.text = getString(R.string.unknown)
		}

		// Country
		tvCountry.text = user.country.takeIf { it.isNotBlank() } ?: getString(R.string.unknown)

		// Range preferences (show with unit km if present)
		val range = user.rangePreferences
		if (range != null) {
			tvRangePreferences.text = String.format("%.1f km", range)
		} else {
			tvRangePreferences.text = getString(R.string.unknown)
		}
	}

	private fun showError(message: String) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show()
		tvUsername.text = getString(R.string.unknown)
		tvEmail.text = getString(R.string.unknown)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		if (item.itemId == android.R.id.home) {
			finish()
			return true
		}
		return super.onOptionsItemSelected(item)
	}

	private fun setupButtonClickListeners() {
		btnModifyProfile.setOnClickListener {
			enterEditMode()
		}

		btnSaveProfile.setOnClickListener {
			saveChanges()
		}

		btnCancelProfile.setOnClickListener {
			exitEditMode()
		}

		// Date picker button
		btnSelectDate.setOnClickListener {
			showDatePicker()
		}

		// Range slider
		seekbarRange.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
				// Convert progress (0-140) to range (1.0-15.0)
				selectedRange = 1.0f + (progress / 10.0f)
				tvRangeDisplay.text = String.format("%.1f km", selectedRange)
			}

			override fun onStartTrackingTouch(seekBar: SeekBar?) {}
			override fun onStopTrackingTouch(seekBar: SeekBar?) {}
		})
	}

	private fun showDatePicker() {
		val calendar = Calendar.getInstance()
		val year = calendar.get(Calendar.YEAR)
		val month = calendar.get(Calendar.MONTH)
		val day = calendar.get(Calendar.DAY_OF_MONTH)

		val datePickerDialog = DatePickerDialog(
			this,
			{ _, selectedYear, selectedMonth, selectedDay ->
				// Format date as DD/MM/YYYY
				selectedBirthday = String.format("%02d/%02d/%d", selectedDay, selectedMonth + 1, selectedYear)
				btnSelectDate.text = selectedBirthday
			},
			year,
			month,
			day
		)

		datePickerDialog.show()
	}

	private fun enterEditMode() {
		isEditMode = true

		// Copy current text values to EditTexts
		etFullname.setText(tvFullname.text.toString())
		etUsername.setText(tvUsername.text.toString())
		etEmail.setText(tvEmail.text.toString())
		etCountry.setText(tvCountry.text.toString())
		
		// Set current values for special controls
		selectedBirthday = tvBirthday.text.toString()
		btnSelectDate.text = selectedBirthday
		
		// Parse and set current range
		val currentRangeText = tvRangePreferences.text.toString()
		val rangeMatch = Regex("([0-9.]+)").find(currentRangeText)
		if (rangeMatch != null) {
			selectedRange = rangeMatch.value.toFloat()
			// Ensure range doesn't exceed new maximum of 15.0km
			if (selectedRange > 15.0f) {
				selectedRange = 15.0f
			}
		} else {
			selectedRange = 1.0f
		}
		
		// Set slider position (convert 1.0-15.0 to 0-140)
		val sliderProgress = ((selectedRange - 1.0f) * 10.0f).toInt()
		seekbarRange.progress = sliderProgress
		tvRangeDisplay.text = String.format("%.1f km", selectedRange)

		// Hide TextViews, show EditTexts and special controls
		tvFullname.visibility = android.view.View.GONE
		tvUsername.visibility = android.view.View.GONE
		tvEmail.visibility = android.view.View.GONE
		tvBirthday.visibility = android.view.View.GONE
		tvCountry.visibility = android.view.View.GONE
		tvRangePreferences.visibility = android.view.View.GONE

		etFullname.visibility = android.view.View.VISIBLE
		etUsername.visibility = android.view.View.VISIBLE
		etEmail.visibility = android.view.View.VISIBLE
		etCountry.visibility = android.view.View.VISIBLE
		btnSelectDate.visibility = android.view.View.VISIBLE
		layoutRangeSlider.visibility = android.view.View.VISIBLE

		// Switch buttons
		btnModifyProfile.visibility = android.view.View.GONE
		layoutEditButtons.visibility = android.view.View.VISIBLE
	}

	private fun exitEditMode() {
		isEditMode = false

		// Show TextViews, hide EditTexts and special controls
		tvFullname.visibility = android.view.View.VISIBLE
		tvUsername.visibility = android.view.View.VISIBLE
		tvEmail.visibility = android.view.View.VISIBLE
		tvBirthday.visibility = android.view.View.VISIBLE
		tvCountry.visibility = android.view.View.VISIBLE
		tvRangePreferences.visibility = android.view.View.VISIBLE

		etFullname.visibility = android.view.View.GONE
		etUsername.visibility = android.view.View.GONE
		etEmail.visibility = android.view.View.GONE
		etCountry.visibility = android.view.View.GONE
		btnSelectDate.visibility = android.view.View.GONE
		layoutRangeSlider.visibility = android.view.View.GONE

		// Switch buttons
		btnModifyProfile.visibility = android.view.View.VISIBLE
		layoutEditButtons.visibility = android.view.View.GONE
	}

	private fun saveChanges() {
		// Validate required fields
		val username = etUsername.text.toString().trim()
		val email = etEmail.text.toString().trim()
		val fullname = etFullname.text.toString().trim()
		val country = etCountry.text.toString().trim()

		if (username.isEmpty() || email.isEmpty()) {
			Toast.makeText(this, "Username and email are required", Toast.LENGTH_SHORT).show()
			return
		}

		if (currentUserId == null) {
			Toast.makeText(this, "User ID not available", Toast.LENGTH_SHORT).show()
			return
		}

		// Parse fullname into firstname and lastname
		val nameParts = fullname.split(" ", limit = 2)
		val firstname = nameParts.getOrNull(0) ?: ""
		val lastname = nameParts.getOrNull(1) ?: ""

		// Convert birthday from DD/MM/YYYY to ISO 8601 format (avoiding timezone issues)
		val birthdayIso: String = try {
			if (selectedBirthday.isNotBlank() && selectedBirthday != getString(R.string.unknown)) {
				// Parse DD/MM/YYYY format and extract components directly
				val inputFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
				val birthDate = inputFormatter.parse(selectedBirthday)
				
				if (birthDate != null) {
					// Extract day, month, year from parsed date
					val calendar = Calendar.getInstance()
					calendar.time = birthDate
					val year = calendar.get(Calendar.YEAR)
					val month = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
					val day = calendar.get(Calendar.DAY_OF_MONTH)
					
					// Create ISO string directly without timezone conversion
					String.format("%04d-%02d-%02dT00:00:00Z", year, month, day)
				} else {
					// Fallback to current date
					val now = Calendar.getInstance()
					String.format("%04d-%02d-%02dT00:00:00Z", 
						now.get(Calendar.YEAR), 
						now.get(Calendar.MONTH) + 1, 
						now.get(Calendar.DAY_OF_MONTH))
				}
			} else {
				// Use current date as fallback
				val now = Calendar.getInstance()
				String.format("%04d-%02d-%02dT00:00:00Z", 
					now.get(Calendar.YEAR), 
					now.get(Calendar.MONTH) + 1, 
					now.get(Calendar.DAY_OF_MONTH))
			}
		} catch (e: Exception) {
			Log.e("ProfileActivity", "Error parsing birthday: $selectedBirthday", e)
			// Use current date as fallback
			val now = Calendar.getInstance()
			String.format("%04d-%02d-%02dT00:00:00Z", 
				now.get(Calendar.YEAR), 
				now.get(Calendar.MONTH) + 1, 
				now.get(Calendar.DAY_OF_MONTH))
		}

		// Create update request using same format as CreateUserRequest
		val updateRequest = UpdateUserRequest(
			firstname = firstname,
			lastname = lastname,
			username = username,
			email = email,
			birthday = birthdayIso,
			country = country,
			avatarUrl = null,
			preferences = null,
			rangePreferences = selectedRange
		)

		// Show loading state
		btnSaveProfile.isEnabled = false
		btnSaveProfile.text = "Saving..."

		// Make API call
		lifecycleScope.launch {
			try {
				val response = userRepository.updateUser(currentUserId!!, updateRequest)
				if (response.isSuccessful) {
					// Update TextViews with edited values
					tvFullname.text = fullname
					tvUsername.text = username
					tvEmail.text = email
					tvBirthday.text = selectedBirthday
					tvCountry.text = country
					tvRangePreferences.text = String.format("%.1f km", selectedRange)

					Toast.makeText(this@ProfileActivity, "Profile saved successfully!", Toast.LENGTH_SHORT).show()
					
					// Exit edit mode
					exitEditMode()
				} else {
					Toast.makeText(this@ProfileActivity, "Failed to save profile: ${response.code()}", Toast.LENGTH_LONG).show()
					Log.e("ProfileActivity", "Server returned error: ${response.code()}")
				}
			} catch (e: Exception) {
				Toast.makeText(this@ProfileActivity, "Error saving profile: ${e.message}", Toast.LENGTH_LONG).show()
				Log.e("ProfileActivity", "Error saving profile", e)
			} finally {
				// Restore button state
				btnSaveProfile.isEnabled = true
				btnSaveProfile.text = "Save"
			}
		}
	}
}