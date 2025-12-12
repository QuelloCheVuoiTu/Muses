package it.unisannio.muses

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.mapbox.maps.MapView
import it.unisannio.muses.R
import it.unisannio.muses.activities.LoginActivity
import it.unisannio.muses.activities.WeatherActivity
import it.unisannio.muses.activities.DailyRewardActivity
import it.unisannio.muses.activities.RewardActivity
import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.data.models.FCMTokenRequestBody
import it.unisannio.muses.helpers.AuthTokenManager
import it.unisannio.muses.helpers.FCMTokenManager
import it.unisannio.muses.helpers.setupMap
import it.unisannio.muses.helpers.OnMuseumClickListener
import it.unisannio.muses.helpers.OnVehicleClickListener
import kotlinx.coroutines.launch
import it.unisannio.muses.data.repositories.UserRepository
import it.unisannio.muses.data.repositories.MissionRepository
import it.unisannio.muses.data.repositories.QuestRepository
import it.unisannio.muses.data.repositories.NavigationRepository
import it.unisannio.muses.data.models.User
import it.unisannio.muses.data.models.Museum
import it.unisannio.muses.data.models.Vehicle
import it.unisannio.muses.data.models.LocationRequestBody
import it.unisannio.muses.data.models.Mission
import it.unisannio.muses.data.models.Quest
import it.unisannio.muses.repository.ChatRepository
import it.unisannio.muses.utils.TextFormatUtils
import retrofit2.Response
import com.google.android.material.navigation.NavigationView
import it.unisannio.muses.activities.PreferencesActivity
import it.unisannio.muses.activities.ProfileActivity
import it.unisannio.muses.utils.ThemeManager
import it.unisannio.muses.activities.MissionActivity
import it.unisannio.muses.activities.QuestDetailActivity
import it.unisannio.muses.activities.ArtworksActivity
import android.widget.ImageView
import android.widget.TextView
import android.widget.EditText
import androidx.cardview.widget.CardView
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import it.unisannio.muses.data.models.ChatMessage
import it.unisannio.muses.adapters.ChatAdapter
import android.view.inputmethod.InputMethodManager
import com.squareup.picasso.Picasso
import it.unisannio.muses.weather.WeatherRepository
import it.unisannio.muses.weather.WeatherResponse
import kotlin.math.roundToInt
import com.google.gson.JsonElement
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import it.unisannio.muses.utils.PolylineUtils
import com.mapbox.geojson.Point
import com.mapbox.geojson.LineString
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource

class MainActivity : ComponentActivity(), OnMuseumClickListener, OnVehicleClickListener {
    // Listener per il tracking della posizione durante la navigazione
    private var locationListener: android.location.LocationListener? = null
    private var userId: String? = null
//    private var userId = "68b6c5fa5e7fd7af3fc17c55"
    private lateinit var tokenManager: FCMTokenManager
    private lateinit var authTokenManager: AuthTokenManager
    private val chatRepository = ChatRepository()
    private val navigationRepository = NavigationRepository()
    
    // Chat components
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var locationManager: LocationManager
    private val chatMessages = mutableListOf<ChatMessage>()
    
    // Variabili per la quest attiva nel widget
    private var currentActiveQuestId: String? = null
    private var currentActiveMissionId: String? = null
    private var currentActiveQuestTitle: String? = null
    
    // MapView reference for navigation
    private lateinit var mapView: MapView
    
    // Track if navigation route is displayed
    private var isRouteDisplayed = false

    // Store the current route coordinates for progressive removal
    private var currentRouteCoordinates: MutableList<Point>? = null

    // Track if navigation is active
    private var isNavigating: Boolean = false
    
    // Proximity threshold for route tracking (in meters)
    private val ROUTE_PROXIMITY_THRESHOLD = 20.0 // 20 meters
    
    // Throttle route updates to prevent crashes
    private var lastRouteUpdateTime = 0L
    
    // Store the currently selected museum for artwork display
    private var currentMuseum: Museum? = null
    
    // Track vehicle booking state
    private var isVehicleBooked = false

//    private val activityResultLauncher = registerForActivityResult(
//        ActivityResultContracts.RequestPermission()
//    ) { isGranted: Boolean ->
//        if (isGranted) {
//            Toast.makeText(this, "Post notification permission granted!", Toast.LENGTH_SHORT).show()
//        } else {
//            Toast.makeText(this, "Post notification permission not granted", Toast.LENGTH_SHORT).show()
//        }
//    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted.
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted.
            } else -> {
            // No location access granted.
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Inizializza il tema prima di tutto
        ThemeManager.initializeTheme(this)
        
        super.onCreate(savedInstanceState)

        // If there's no saved auth token, redirect to LoginActivity
        authTokenManager = AuthTokenManager(this)

        val savedToken = authTokenManager.getToken()
        if (savedToken.isNullOrEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        Log.d("MainActivity", "Saved token: $savedToken")

        // Wire RetrofitInstance tokenProvider to use the AuthTokenManager instance.
        // This will make the Retrofit client include `Authorization: Bearer <token>`
        // for requests when a token is available.
        RetrofitInstance.tokenProvider = { authTokenManager.getToken() }

        // If an entityId was stored at login, use it as the userId
        val savedEntityId = authTokenManager.getEntityId()
        if (!savedEntityId.isNullOrEmpty()) {
            userId = savedEntityId
            Log.d("MainActivity", "Using saved entityId as userId: $userId")
            
            // Fetch and log the user details from the API
            val userRepository = UserRepository()
            lifecycleScope.launch {
                try {
                    val response: Response<User> = userRepository.getUser(userId!!)
                    if (response.isSuccessful) {
                        val user = response.body()
                        Log.d("MainActivity", "Retrieved user: $user")
                    } else {
                        Log.e("MainActivity", "Failed to retrieve user $userId. Code: ${response.code()} Message: ${response.errorBody()?.string()}")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error while fetching user", e)
                }
            }
        } else {
            Log.w("MainActivity", "No savedEntityId found, skipping user fetch")
        }

        // enableEdgeToEdge() - commented out to fix drawer issues
        setContentView(R.layout.activity_main)
        
        // Initialize LocationManager for weather widget GPS functionality
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // Ensure the navigation drawer is rendered above MapView (Mapbox MapView can draw on top)
        // We adjust elevation and bring the nav view to front so it appears as a proper side drawer.
    val drawerLayoutView = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout)
        // Commented out elevation adjustments for modern phone compatibility
        // drawerLayoutView?.apply {
        //     // give the drawer an elevation so it stacks above the MapView
        //     this.elevation = 32f
        // }
        // navViewOverlay?.apply {
        //     this.elevation = 40f
        //     // If MapView uses a SurfaceView-ish implementation, ensure nav view is brought to front
        //     this.bringToFront()
        //     this.invalidate()
        // }

        // Add drawer listener to handle keyboard/chat conflicts
        drawerLayoutView.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            
            override fun onDrawerOpened(drawerView: View) {
                // Ensure keyboard is hidden when drawer opens
                hideKeyboard()
            }
            
            override fun onDrawerClosed(drawerView: View) {}
            
            override fun onDrawerStateChanged(newState: Int) {
                if (newState == androidx.drawerlayout.widget.DrawerLayout.STATE_DRAGGING) {
                    // User started dragging drawer, hide keyboard
                    hideKeyboard()
                }
            }
        })

        // Find the MapView from the XML layout
        mapView = findViewById<MapView>(R.id.mapView)

        // Call setupMap with the MapView from XML and museum click listener
        setupMap(this, mapView, this, this)
        
        // Check if we were launched with navigation data from QuestActivity
        checkForNavigationIntent()

        // Example of setting up other views from the XML
        val fabChat = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabChat)
    val fabMenu = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabMenu)
        val fabStopNavigation = findViewById<MaterialButton>(R.id.fabStopNavigation)
        val chatContainer = findViewById<androidx.cardview.widget.CardView>(R.id.chatContainer)
        val btnCloseChat = findViewById<android.widget.ImageButton>(R.id.btnCloseChat)
        
        // Chat input components
        val editTextMessage = findViewById<EditText>(R.id.editTextMessage)
        val btnSendMessage = findViewById<ImageButton>(R.id.btnSendMessage)
        val recyclerViewMessages = findViewById<RecyclerView>(R.id.recyclerViewMessages)
        
        // Initialize chat RecyclerView
        chatAdapter = ChatAdapter(chatMessages)
        recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        // Auto-scroll to bottom when keyboard opens
        editTextMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && chatAdapter.itemCount > 0) {
                recyclerViewMessages.postDelayed({
                    recyclerViewMessages.scrollToPosition(chatAdapter.itemCount - 1)
                }, 200) // Small delay to allow keyboard to open
            }
        }

        // Museum detail card views
        val museumDetailCard = findViewById<CardView>(R.id.museumDetailCard)
        val btnCloseMuseumCard = findViewById<ImageButton>(R.id.btnCloseMuseumCard)
        val btnShowArtworks = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnShowArtworks)
        val btnNavigate = findViewById<MaterialButton>(R.id.btnNavigate)
        val museumImage = findViewById<ImageView>(R.id.museumImage)
        val museumName = findViewById<TextView>(R.id.museumName)
        val museumDescription = findViewById<TextView>(R.id.museumDescription)
        val museumHours = findViewById<TextView>(R.id.museumHours)
        val museumPrice = findViewById<TextView>(R.id.museumPrice)

        // Vehicle detail card views
        val vehicleDetailCard = findViewById<CardView>(R.id.vehicleDetailCard)
        val btnCloseVehicleCard = findViewById<ImageButton>(R.id.btnCloseVehicleCard)
        val btnRentVehicle = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRentVehicle)

        // Widget meteo views
        val weatherWidget = findViewById<CardView>(R.id.weatherWidget)

        // Widget preferenze views
        val preferencesWidget = findViewById<CardView>(R.id.preferencesWidget)

        // Widget missioni views
        val missionWidget = findViewById<CardView>(R.id.missionWidget)

        // Setup weather widget
        setupWeatherWidget()

        // Setup preferences widget
        setupPreferencesWidget()

        // Setup mission widget
        setupMissionWidget()

        // Click listener per aprire pagina meteo completa
        weatherWidget.setOnClickListener {
            val weatherIntent = Intent(this, WeatherActivity::class.java)
            startActivity(weatherIntent)
        }

        // Click listener per aprire pagina preferenze
        preferencesWidget.setOnClickListener {
            val prefIntent = Intent(this, PreferencesActivity::class.java)
            authTokenManager.getEntityId()?.let { prefIntent.putExtra(PreferencesActivity.EXTRA_USER_ID, it) }
            startActivity(prefIntent)
        }

        // Click listener per aprire la quest attiva direttamente
        missionWidget.setOnClickListener {
            Log.d("MissionWidget", "Widget clicked - QuestId: $currentActiveQuestId, MissionId: $currentActiveMissionId")
            
            if (currentActiveQuestId != null && currentActiveMissionId != null) {
                // Apri direttamente la pagina delle task della quest attiva
                Log.d("MissionWidget", "Opening QuestDetailActivity for quest: $currentActiveQuestId")
                val questDetailIntent = Intent(this, QuestDetailActivity::class.java).apply {
                    putExtra(QuestDetailActivity.EXTRA_QUEST_ID, currentActiveQuestId)
                    putExtra(QuestDetailActivity.EXTRA_MISSION_ID, currentActiveMissionId)
                    putExtra(QuestDetailActivity.EXTRA_QUEST_TITLE, currentActiveQuestTitle ?: "Quest Attiva")
                }
                startActivity(questDetailIntent)
            } else {
                // Fallback: apri la pagina delle missioni se non c'è quest attiva
                Log.d("MissionWidget", "No active quest found, opening MissionActivity")
                val missionIntent = Intent(this, MissionActivity::class.java)
                startActivity(missionIntent)
            }
        }

        // Navigation drawer: open Profile activity when profile menu item is clicked
        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java)
                    // Optionally pass the saved entity id
                    val authTokenManager = AuthTokenManager(this)
                    authTokenManager.getEntityId()?.let { intent.putExtra(ProfileActivity.EXTRA_USER_ID, it) }
                    startActivity(intent)
                    true
                }
                R.id.nav_weather -> {
                    val weatherIntent = Intent(this, WeatherActivity::class.java)
                    startActivity(weatherIntent)
                    drawerLayoutView.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_preferences -> {
                    // Open the PreferencesActivity and pass the user id if available
                    val prefIntent = Intent(this, PreferencesActivity::class.java)
                    authTokenManager.getEntityId()?.let { prefIntent.putExtra(PreferencesActivity.EXTRA_USER_ID, it) }
                    startActivity(prefIntent)
                    true
                }
                R.id.nav_daily_login -> {
                    val dailyRewardIntent = Intent(this, DailyRewardActivity::class.java)
                    startActivity(dailyRewardIntent)
                    drawerLayoutView.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_reward -> {
                    val rewardIntent = Intent(this, RewardActivity::class.java)
                    startActivity(rewardIntent)
                    drawerLayoutView.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_missions -> {
                    val missionsIntent = Intent(this, MissionActivity::class.java)
                    startActivity(missionsIntent)
                    true
                }
                R.id.nav_logout -> {
                    // Clear stored auth token and any saved entity id
                    authTokenManager.clearAll()

                    // Redirect user to LoginActivity and clear activity back stack so user can't go back
                    val loginIntent = Intent(this, LoginActivity::class.java)
                    loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(loginIntent)
                    finish()
                    true
                }
                else -> false
            }
        }

        fabChat.setOnClickListener {
            // Toggle the visibility of the chat container
            if (chatContainer.visibility == View.VISIBLE) {
                chatContainer.visibility = View.GONE
            } else {
                // Ensure chat container is above other views (FABs / MapView)
                chatContainer.bringToFront()
                chatContainer.invalidate()
                chatContainer.visibility = View.VISIBLE
                // Also raise elevation in runtime in case MapView draws on top
                chatContainer.elevation = 24f
                chatContainer.translationZ = 24f
            }
        }

        // Close chat when the X button is clicked
        btnCloseChat.setOnClickListener {
            if (chatContainer.visibility == View.VISIBLE) {
                // Hide keyboard immediately using the edit text
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(editTextMessage.windowToken, 0)
                
                // Clear focus from edit text
                editTextMessage.clearFocus()
                
                // Close chat with a small delay to ensure keyboard closes
                editTextMessage.postDelayed({
                    chatContainer.visibility = View.GONE
                    
                    // Force layout refresh to prevent drawer issues
                    val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout)
                    drawerLayout?.requestLayout()
                }, 100)
            }
        }

        // Close museum card when the X button is clicked
        btnCloseMuseumCard.setOnClickListener {
            if (museumDetailCard.visibility == View.VISIBLE) {
                museumDetailCard.visibility = View.GONE
            }
        }

        // Close vehicle card when the X button is clicked
        btnCloseVehicleCard.setOnClickListener {
            if (vehicleDetailCard.visibility == View.VISIBLE) {
                vehicleDetailCard.visibility = View.GONE
            }
        }

        // Show artworks button click listener
        btnShowArtworks.setOnClickListener {
            currentMuseum?.let { museum ->
                val intent = ArtworksActivity.newIntent(this, museum.id, museum.name)
                startActivity(intent)
            }
        }

        // Navigate button click listener
        btnNavigate.setOnClickListener {
            currentMuseum?.let { museum ->
                navigateToMuseum(museum)
            }
        }

        // Rent vehicle button click listener
        btnRentVehicle.setOnClickListener {
            toggleVehicleBooking(btnRentVehicle)
        }

        // Open the navigation drawer when the left menu FAB is clicked
        fabMenu.setOnClickListener {
            // Close chat if open to avoid conflicts with drawer
            if (chatContainer.visibility == View.VISIBLE) {
                chatContainer.visibility = View.GONE
            }
            // Hide keyboard and clear focus before opening drawer to avoid layout conflicts
            hideKeyboard()
            editTextMessage.clearFocus()
            drawerLayoutView.openDrawer(GravityCompat.START)
        }

        // Stop navigation FAB click listener
        fabStopNavigation.setOnClickListener {
            clearRouteFromMap()
        }

        // Send chat message when send button is clicked
        btnSendMessage.setOnClickListener {
            val prompt = editTextMessage.text.toString().trim()
            if (prompt.isNotEmpty()) {
                sendChatMessage(prompt, editTextMessage, recyclerViewMessages)
            } else {
                Toast.makeText(this, "Scrivi un messaggio prima di inviare", Toast.LENGTH_SHORT).show()
            }
        }

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//                activityResultLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
//            }
//        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        tokenManager = FCMTokenManager(this)

        // Check if the token has been sent to the server before.
        // This prevents unnecessary network calls on every app launch.
        if (!tokenManager.isTokenSentToServer()) {
            sendTokenToServer()
        }

        // For now, just send the token every time
        sendTokenToServer()
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume() - Refreshing all widgets")
        // Refresh weather when app becomes active
        setupWeatherWidget()
        // Refresh preferences when app becomes active
        setupPreferencesWidget()
        // Refresh mission widget when app becomes active
        setupMissionWidget()
    }

    private fun sendTokenToServer() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                val errorMessage = task.exception?.message ?: "Unknown error occurred"
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                return@addOnCompleteListener // Use return@addOnCompleteListener to return from the lambda
            }

            // Get new FCM registration token
            val token = task.result
            Log.d("FCM_TOKEN", token)

            lifecycleScope.launch {
                try {
                    userId?.let { uid ->
                        RetrofitInstance.api.sendToken(FCMTokenRequestBody(uid, token))

                        // After successful network call, set the flag.
                        tokenManager.setTokenSentToServer(true)

                        Log.d("MainActivity", "Token sent successfully to $uid and flag updated")
                    } ?: Log.w("MainActivity", "userId is null, cannot send FCM token")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error sending token to $userId", e)
                }
            }
        }
    }

    override fun onMuseumClicked(museum: Museum) {
        // Store the current museum for artwork display
        currentMuseum = museum
        
        // Find the museum card views
        val museumDetailCard = findViewById<CardView>(R.id.museumDetailCard)
        val museumImage = findViewById<ImageView>(R.id.museumImage)
        val museumName = findViewById<TextView>(R.id.museumName)
        val museumNameOverlay = findViewById<TextView>(R.id.museumNameOverlay)
        val museumDescription = findViewById<TextView>(R.id.museumDescription)
        val museumHours = findViewById<TextView>(R.id.museumHours)
        val museumPrice = findViewById<TextView>(R.id.museumPrice)

        // Populate the card with museum data
        museumName.text = museum.name
        museumNameOverlay.text = museum.name  // New overlay text
        museumDescription.text = museum.description
        museumHours.text = museum.hours
        museumPrice.text = museum.price

        // Load museum image using Picasso
        if (museum.imageUrl.isNotEmpty()) {
            Picasso.get()
                .load(museum.imageUrl)
                .placeholder(R.drawable.ic_museum)
                .error(R.drawable.ic_museum)
                .into(museumImage)
        } else {
            museumImage.setImageResource(R.drawable.ic_museum)
        }

        // Show the museum card with proper elevation
        museumDetailCard.bringToFront()
        museumDetailCard.invalidate()
        museumDetailCard.visibility = View.VISIBLE
        museumDetailCard.elevation = 32f
        museumDetailCard.translationZ = 32f

        Log.d("MainActivity", "Displaying museum card for: ${museum.name}")
    }
    private fun sendChatMessage(prompt: String, editText: EditText, recyclerView: RecyclerView) {
        Log.d("MainActivity", "Sending chat message: $prompt")
        
        // Add user message to chat immediately
        val userMessage = ChatMessage(prompt, isFromUser = true)
        chatAdapter.addMessage(userMessage)
        
        // Show typing indicator
        chatAdapter.showTypingIndicator()
        
        // Scroll to the bottom to show the new message
        recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
        
        // Clear the input field immediately
        editText.text.clear()
        
        lifecycleScope.launch {
            try {
                val response = chatRepository.generateChatResponse(prompt)
                
                // Hide typing indicator
                chatAdapter.hideTypingIndicator()
                
                if (response.isSuccessful) {
                    val chatResponse = response.body()
                    Log.d("MainActivity", "Chat response received: ${chatResponse?.response}")
                    
                    // Add bot response to chat
                    chatResponse?.response?.let { responseText ->
                        runOnUiThread {
                            val botMessage = ChatMessage(responseText, isFromUser = false)
                            chatAdapter.addMessage(botMessage)
                            recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                        }
                    }
                } else {
                    Log.e("MainActivity", "Chat API error. Code: ${response.code()}")
                    runOnUiThread {
                        val errorMessage = ChatMessage("Mi dispiace, c'è stato un errore. Riprova più tardi.", isFromUser = false)
                        chatAdapter.addMessage(errorMessage)
                        recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                    }
                }
            } catch (e: Exception) {
                // Hide typing indicator on error too
                chatAdapter.hideTypingIndicator()
                
                Log.e("MainActivity", "Exception during chat request", e)
                runOnUiThread {
                    val errorMessage = ChatMessage("Errore di connessione. Controlla la tua connessione internet.", isFromUser = false)
                    chatAdapter.addMessage(errorMessage)
                    recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                }
            }
        }
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocusedView = currentFocus
        if (currentFocusedView != null) {
            inputMethodManager.hideSoftInputFromWindow(
                currentFocusedView.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
        }
    }

    private fun setupWeatherWidget() {
        Log.d("MainActivity", "Refreshing weather widget...")
        // Controlla i permessi GPS e carica meteo di conseguenza
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            
            // Permessi concessi - tenta di ottenere coordinate GPS
            getCurrentLocationAndLoadWeatherWidget()
        } else {
            // Permessi non concessi - mostra messaggio di localizzazione richiesta
            showLocationRequiredMessageInWidget()
        }
    }
    
    private fun getCurrentLocationAndLoadWeatherWidget() {
        try {
            // Ottieni l'ultima posizione nota
            val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            if (lastKnownLocation != null) {
                Log.d("MainActivity", "Using GPS location for weather widget: ${lastKnownLocation.latitude}, ${lastKnownLocation.longitude}")
                loadWeatherWidgetForLocation(lastKnownLocation.latitude, lastKnownLocation.longitude)
            } else {
                Log.w("MainActivity", "No GPS location available for weather widget")
                showLocationRequiredMessageInWidget()
            }
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Location permission error for weather widget: ${e.message}")
            showLocationRequiredMessageInWidget()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting location for weather widget: ${e.message}")
            showLocationRequiredMessageInWidget()
        }
    }
    
    private fun loadWeatherWidgetForLocation(latitude: Double, longitude: Double) {
        val weatherRepository = WeatherRepository(this)
        
        weatherRepository.getWeatherDataForLocation(latitude, longitude, "Posizione Attuale", object : WeatherRepository.WeatherCallback {
            override fun onSuccess(weather: WeatherResponse) {
                runOnUiThread {
                    updateWeatherWidget(weather)
                }
            }
            
            override fun onError(message: String) {
                Log.e("MainActivity", "Weather error for GPS location: $message")
                runOnUiThread {
                    showLocationRequiredMessageInWidget()
                }
            }
            
            override fun onLoading() {
                // Widget mostra valori di default durante il caricamento
            }
        })
    }
    
    private fun showLocationRequiredMessageInWidget() {
        val tvTemperature = findViewById<TextView>(R.id.tvTemperature)
        val tvHumidity = findViewById<TextView>(R.id.tvHumidity)
        val ivWeatherIcon = findViewById<ImageView>(R.id.ivWeatherIcon)

        // Mostra messaggio informativo nel widget
        tvTemperature.text = "Attivare"
        tvHumidity.text = "GPS"
        ivWeatherIcon.setImageResource(R.drawable.ic_location)
    }

    private fun updateWeatherWidget(weather: WeatherResponse) {
        val ivWeatherIcon = findViewById<ImageView>(R.id.ivWeatherIcon)
        val tvTemperature = findViewById<TextView>(R.id.tvTemperature)
        val tvHumidity = findViewById<TextView>(R.id.tvHumidity)

        // Aggiorna temperatura
        tvTemperature.text = "${weather.main.temp.roundToInt()}°C"
        
        // Aggiorna umidità con etichetta
        tvHumidity.text = "Umidità: ${weather.main.humidity}%"
        
        // Aggiorna icona meteo
        val weatherIcon = getWeatherIcon(weather.weather[0].main)
        ivWeatherIcon.setImageResource(weatherIcon)
    }

    private fun getWeatherIcon(weatherMain: String): Int {
        return when (weatherMain.lowercase()) {
            "clear" -> R.drawable.ic_sunny
            "clouds" -> R.drawable.ic_cloudy
            "rain", "drizzle" -> R.drawable.ic_rainy
            "thunderstorm" -> R.drawable.ic_rainy
            "snow" -> R.drawable.ic_cloudy
            "mist", "fog", "haze" -> R.drawable.ic_cloudy
            else -> R.drawable.ic_sunny
        }
    }

    private fun setupMissionWidget() {
        Log.d("MainActivity", "Refreshing mission widget...")
        // Recupera le missioni reali dell'utente dall'API
        lifecycleScope.launch {
            try {
                val missions = loadUserMissions()
                runOnUiThread {
                    updateMissionWidget(missions)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading missions", e)
                runOnUiThread {
                    updateMissionWidget(emptyList()) // Nessuna missione
                }
            }
        }
    }

    private suspend fun loadUserMissions(): List<Mission> {
        return try {
            userId?.let { uid ->
                val repository = MissionRepository()
                val response = repository.getUserMissions(uid)
                
                if (response.isSuccessful) {
                    val missions = response.body() ?: emptyList()
                    Log.d("MainActivity", "Loaded ${missions.size} missions for widget")
                    missions
                } else {
                    Log.w("MainActivity", "Failed to load missions: ${response.code()}")
                    emptyList()
                }
            } ?: run {
                Log.w("MainActivity", "userId is null, cannot load missions")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Exception loading missions", e)
            emptyList()
        }
    }

    private fun updateMissionWidget(missions: List<Mission>) {
        val tvMissionTitle = findViewById<TextView>(R.id.tvMissionTitle)
        val tvMissionProgress = findViewById<TextView>(R.id.tvMissionProgress)

        // Reset variabili quest attiva
        currentActiveQuestId = null
        currentActiveMissionId = null
        currentActiveQuestTitle = null

        Log.d("MissionWidget", "Updating mission widget with ${missions.size} missions")

        if (missions.isEmpty()) {
            // Nessuna missione - stesso testo della pagina missioni
            tvMissionTitle.text = "Nessuna missione"
            tvMissionProgress.text = ""
            return
        } 

        // Cerca la prima mission IN_PROGRESS
        val inProgressMission = missions.find { it.status.equals("IN_PROGRESS", ignoreCase = true) }
        
        if (inProgressMission != null) {
            Log.d("MissionWidget", "Found IN_PROGRESS mission: ${inProgressMission.id}")
        } else {
            Log.d("MissionWidget", "No IN_PROGRESS mission found")
        }
        
        if (inProgressMission == null) {
            // Nessuna mission in progress - mostra la prima missione disponibile
            val firstMission = missions.first()
            Log.d("MissionWidget", "No IN_PROGRESS mission, showing first mission: ${firstMission.id}")
            tvMissionTitle.text = "Mission ${firstMission.id.takeLast(8)}"
            
            // Carica il titolo della prima quest in modo asincrono
            loadFirstQuestTitleForWidget(firstMission, tvMissionTitle)
            
            // Calcola il progresso basandosi sui steps completati
            val completedSteps = firstMission.steps.count { it.completed }
            val totalSteps = firstMission.steps.size
            tvMissionProgress.text = "$completedSteps/$totalSteps completato"
            return
        }

        // Mission in progress trovata - cerca la prima quest IN_PROGRESS
        currentActiveMissionId = inProgressMission.id
        
        Log.d("MissionWidget", "Setting active mission: $currentActiveMissionId with ${inProgressMission.steps.size} steps")
        
        if (inProgressMission.steps.isEmpty()) {
            tvMissionTitle.text = "Mission ${inProgressMission.id.takeLast(8)}"
            tvMissionProgress.text = "Nessuna quest disponibile"
            return
        }

        // Carica tutte le quest per trovare quella in progress
        lifecycleScope.launch {
            try {
                val questRepository = QuestRepository()
                var firstInProgressQuest: Quest? = null
                var questFound = false
                
                Log.d("MissionWidget", "Searching for IN_PROGRESS quest in mission steps")
                
                for (step in inProgressMission.steps) {
                    if (questFound) break
                    
                    Log.d("MissionWidget", "Checking quest: ${step.stepId}")
                    val response = questRepository.getQuestById(step.stepId)
                    if (response.isSuccessful) {
                        val quest = response.body()
                        if (quest != null) {
                            Log.d("MissionWidget", "Quest ${quest.id} status: ${quest.status}")
                            if (quest.status.equals("IN_PROGRESS", ignoreCase = true)) {
                                firstInProgressQuest = quest
                                currentActiveQuestId = quest.id
                                questFound = true
                                Log.d("MissionWidget", "Found IN_PROGRESS quest: ${quest.id}")
                            }
                        }
                    }
                }
                
                runOnUiThread {
                    if (firstInProgressQuest != null) {
                        // Quest in progress trovata
                        val questTitle = TextFormatUtils.formatTitle(
                            if (firstInProgressQuest.title.isNotEmpty()) firstInProgressQuest.title else "Quest ${firstInProgressQuest.id.takeLast(8)}"
                        )
                        currentActiveQuestTitle = questTitle
                        tvMissionTitle.text = questTitle
                        
                        Log.d("MissionWidget", "Set active quest: $currentActiveQuestId - $currentActiveQuestTitle")
                        
                        // Mostra progresso della quest
                        val completedTasks = firstInProgressQuest.tasks.values.count { it.completed }
                        val totalTasks = firstInProgressQuest.tasks.size
                        tvMissionProgress.text = "$completedTasks/$totalTasks task completate"
                    } else {
                        // Nessuna quest in progress - mostra la prima quest della mission
                        val firstStep = inProgressMission.steps.first()
                        currentActiveQuestId = firstStep.stepId
                        Log.d("MissionWidget", "No IN_PROGRESS quest found, using first quest: $currentActiveQuestId")
                        loadFirstQuestTitleForWidget(inProgressMission, tvMissionTitle)
                        
                        val completedSteps = inProgressMission.steps.count { it.completed }
                        val totalSteps = inProgressMission.steps.size
                        tvMissionProgress.text = "$completedSteps/$totalSteps quest completate"
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading quest details for mission widget", e)
                runOnUiThread {
                    tvMissionTitle.text = "Mission ${inProgressMission.id.takeLast(8)}"
                    val completedSteps = inProgressMission.steps.count { it.completed }
                    val totalSteps = inProgressMission.steps.size
                    tvMissionProgress.text = "$completedSteps/$totalSteps quest"
                }
            }
        }
    }

    private fun loadFirstQuestTitleForWidget(mission: Mission, titleTextView: TextView) {
        // Se non ci sono steps, mantieni il titolo attuale
        if (mission.steps.isEmpty()) return
        
        // Prendi il primo step (prima quest)
        val firstStep = mission.steps.first()
        val questId = firstStep.stepId
        
        // Carica la quest in background
        lifecycleScope.launch {
            try {
                val questRepository = QuestRepository()
                val response = questRepository.getQuestById(questId)
                
                if (response.isSuccessful) {
                    val quest = response.body()
                    if (quest != null) {
                        val questTitle = TextFormatUtils.formatTitle(
                            if (quest.title.isNotEmpty()) quest.title else "Mission ${mission.id.takeLast(8)}"
                        )
                        // Aggiorna il titolo nella UI thread
                        titleTextView.text = questTitle
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading quest title for mission widget ${mission.id}", e)
                // In caso di errore, mantieni il titolo originale
            }
        }
    }

    private fun toggleVehicleBooking(button: com.google.android.material.button.MaterialButton) {
        isVehicleBooked = !isVehicleBooked
        
        if (isVehicleBooked) {
            // Stato: Prenotato - mostra opzione per cancellare
            button.text = "Cancella Prenotazione"
            button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.ios_red)
            Toast.makeText(this, "Veicolo prenotato con successo!", Toast.LENGTH_SHORT).show()
        } else {
            // Stato: Non prenotato - mostra opzione per prenotare
            button.text = "Prenota Veicolo"
            button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.apple_blue)
            Toast.makeText(this, "Prenotazione cancellata", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onVehicleClicked(vehicle: Vehicle) {
        // Find the vehicle card views
        val vehicleDetailCard = findViewById<CardView>(R.id.vehicleDetailCard)
        val vehicleImage = findViewById<ImageView>(R.id.vehicleImage)
        val vehicleType = findViewById<TextView>(R.id.vehicleType)
        val vehiclePrice = findViewById<TextView>(R.id.vehiclePrice)
        val vehicleLocation = findViewById<TextView>(R.id.vehicleLocation)
        val btnRentVehicle = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRentVehicle)

        // Reset booking state when opening a new vehicle card
        isVehicleBooked = false
        btnRentVehicle.text = "Prenota Veicolo"
        btnRentVehicle.backgroundTintList = ContextCompat.getColorStateList(this, R.color.apple_blue)

        // Populate the card with vehicle data
        vehicleType.text = vehicle.type
        vehiclePrice.text = "€${String.format("%.2f", vehicle.pricePerHour)}/h"
        vehicleLocation.text = vehicle.address

        // Set appropriate vehicle icon based on type
        when (vehicle.type.lowercase()) {
            "bicicletta" -> vehicleImage.setImageResource(R.drawable.bicycle)
            "e-bike" -> vehicleImage.setImageResource(R.drawable.bicycle_e)
            "monopattino" -> vehicleImage.setImageResource(R.drawable.scooter)
            else -> vehicleImage.setImageResource(R.drawable.bicycle)
        }

        // Show the vehicle card with proper elevation
        vehicleDetailCard.bringToFront()
        vehicleDetailCard.invalidate()
        vehicleDetailCard.visibility = View.VISIBLE
        vehicleDetailCard.elevation = 32f
        vehicleDetailCard.translationZ = 32f

        Log.d("MainActivity", "Displaying vehicle card for: ${vehicle.type} - €${vehicle.pricePerHour}/h")
    }

    private fun setupPreferencesWidget() {
        Log.d("MainActivity", "Refreshing preferences widget...")
        val userRepository = UserRepository()
        
        lifecycleScope.launch {
            try {
                userId?.let { uid ->
                    val response = userRepository.getPreferences(uid)
                    if (response.isSuccessful) {
                        val body = response.body()
                        val prefsList: List<String> = when {
                            body == null || body.isJsonNull -> emptyList()
                            body.isJsonArray -> {
                                val arr = body.asJsonArray
                                arr.mapNotNull { element ->
                                    when {
                                        element.isJsonNull -> null
                                        element.isJsonPrimitive -> {
                                            val str = element.asString
                                            if (str == "null" || str.isBlank()) null else str
                                        }
                                        else -> {
                                            val str = element.toString()
                                            if (str == "null" || str.isBlank()) null else str
                                        }
                                    }
                                }
                            }
                        body.isJsonObject -> {
                            val obj = body.asJsonObject
                            when {
                                obj.has("preferences") && obj.get("preferences").isJsonArray -> {
                                    obj.getAsJsonArray("preferences").mapNotNull { element ->
                                        when {
                                            element.isJsonNull -> null
                                            element.isJsonPrimitive -> {
                                                val str = element.asString
                                                if (str == "null" || str.isBlank()) null else str
                                            }
                                            else -> {
                                                val str = element.toString()
                                                if (str == "null" || str.isBlank()) null else str
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    obj.entrySet().mapNotNull { entry ->
                                        val v = entry.value
                                        when {
                                            v.isJsonNull -> null
                                            v.isJsonPrimitive -> {
                                                val str = v.asString
                                                if (str == "null" || str.isBlank()) null else str
                                            }
                                            else -> {
                                                val str = v.toString()
                                                if (str == "null" || str.isBlank()) null else str
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> emptyList()
                    }
                    
                    runOnUiThread {
                        updatePreferencesWidget(prefsList)
                    }
                } else {
                    Log.w("MainActivity", "Failed to load preferences: ${response.code()}")
                    runOnUiThread {
                        updatePreferencesWidget(emptyList())
                    }
                }
            } ?: run {
                Log.w("MainActivity", "userId is null, cannot load preferences")
                runOnUiThread {
                    updatePreferencesWidget(emptyList())
                }
            }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading preferences", e)
                runOnUiThread {
                    updatePreferencesWidget(emptyList())
                }
            }
        }
    }
    
    private fun navigateToMuseum(museum: Museum) {
        // Check location permissions first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            
            Toast.makeText(this, "Location permission required for navigation", Toast.LENGTH_SHORT).show()
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        // Get user's current location
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val lastKnownLocation = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            null
        }

        if (lastKnownLocation == null) {
            Toast.makeText(this, "Unable to get current location. Please make sure GPS is enabled.", Toast.LENGTH_LONG).show()
            return
        }

        val userLat = lastKnownLocation.latitude
        val userLong = lastKnownLocation.longitude
        val museumLat = museum.location.latitude
        val museumLong = museum.location.longitude

        // Show loading indicator
        Toast.makeText(this, "Calculating route...", Toast.LENGTH_SHORT).show()

        // Fetch route from OSRM API
        lifecycleScope.launch {
            try {
                val response = navigationRepository.getRoute(userLong, userLat, museumLong, museumLat)
                
                if (response.isSuccessful) {
                    val osrmResponse = response.body()
                    if (osrmResponse != null && osrmResponse.routes.isNotEmpty()) {
                        val route = osrmResponse.routes[0]
                        val polyline = route.geometry
                        
                        // Decode polyline and display on map
                        displayRouteOnMap(polyline)
                        
                        // Show route info
                        val distanceKm = route.distance / 1000
                        val durationMin = route.duration / 60
                        Toast.makeText(
                            this@MainActivity,
                            String.format("Route found: %.1f km, %.0f minutes walking", distanceKm, durationMin),
                            Toast.LENGTH_LONG
                        ).show()
                        
                        Log.d("MainActivity", "Route calculated successfully: ${distanceKm}km, ${durationMin}min")
                    } else {
                        Toast.makeText(this@MainActivity, "No route found", Toast.LENGTH_SHORT).show()
                        Log.e("MainActivity", "No routes in response")
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Failed to calculate route", Toast.LENGTH_SHORT).show()
                    Log.e("MainActivity", "Route API error. Code: ${response.code()}")
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Network error. Please check your connection.", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "Exception during route request", e)
            }
        }
    }

    private fun displayRouteOnMap(polyline: String) {
        try {
            // Decode the polyline6 into coordinates
            val coordinates = PolylineUtils.decodePolyline6(polyline)
            
            if (coordinates.isEmpty()) {
                Log.w("MainActivity", "No coordinates decoded from polyline")
                return
            }

            // Save route coordinates for progressive removal
            currentRouteCoordinates = coordinates.toMutableList()
            isNavigating = true
            lastRouteUpdateTime = System.currentTimeMillis() // Initialize update timer

            mapView.mapboxMap.getStyle { style ->
                try {
                    // Remove existing route if any
                    if (style.styleLayerExists("route-layer")) {
                        style.removeStyleLayer("route-layer")
                    }
                    if (style.styleSourceExists("route-source")) {
                        style.removeStyleSource("route-source")
                    }
                    
                    // Create LineString from coordinates
                    val lineString = LineString.fromLngLats(coordinates)
                    
                    // Add route source
                    val routeSource = geoJsonSource("route-source") {
                        geometry(lineString)
                    }
                    style.addSource(routeSource)
                    
                    // Add route layer with proper z-index to avoid conflicts with user location
                    val routeLayer = lineLayer("route-layer", "route-source") {
                        lineColor("#007AFF")
                        lineWidth(5.0)
                    }
                    style.addLayer(routeLayer)
                    
                    // Mark route as displayed and show stop navigation button
                    isRouteDisplayed = true
                    val fabStopNavigation = findViewById<MaterialButton>(R.id.fabStopNavigation)
                    fabStopNavigation.visibility = View.VISIBLE
                    
                    Log.d("MainActivity", "Route displayed on map with ${coordinates.size} points")
                    
                    // Start location tracking for progressive route removal
                    startLocationTrackingForRoute()
                    
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error setting up route on map", e)
                    Toast.makeText(this, "Error setting up navigation route", Toast.LENGTH_SHORT).show()
                }
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error displaying route on map", e)
            Toast.makeText(this, "Error displaying route on map", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkForNavigationIntent() {
        val polyline = intent.getStringExtra("NAVIGATION_POLYLINE")
        val museumName = intent.getStringExtra("NAVIGATION_MUSEUM_NAME")
        val distance = intent.getDoubleExtra("NAVIGATION_DISTANCE", 0.0)
        val duration = intent.getDoubleExtra("NAVIGATION_DURATION", 0.0)
        
        if (!polyline.isNullOrEmpty()) {
            // Display the route on the map
            displayRouteOnMap(polyline)
            
            // Show confirmation message
            val message = if (museumName != null) {
                String.format("Navigation started to %s: %.1f km, %.0f minutes walking", museumName, distance, duration)
            } else {
                String.format("Navigation started: %.1f km, %.0f minutes walking", distance, duration)
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            
            // Clear the intent extras to avoid re-displaying on activity recreate
            intent.removeExtra("NAVIGATION_POLYLINE")
            intent.removeExtra("NAVIGATION_MUSEUM_NAME")
            intent.removeExtra("NAVIGATION_DISTANCE")
            intent.removeExtra("NAVIGATION_DURATION")
        }
    }

    private fun clearRouteFromMap() {
        try {
            // Stop location tracking for progressive route updates
            stopNavigationTracking()
            
            mapView.mapboxMap.getStyle { style ->
                // Remove route layer and source
                if (style.styleLayerExists("route-layer")) {
                    style.removeStyleLayer("route-layer")
                }
                if (style.styleSourceExists("route-source")) {
                    style.removeStyleSource("route-source")
                }
                
                // Mark route as not displayed and hide stop navigation button
                isRouteDisplayed = false
                val fabStopNavigation = findViewById<MaterialButton>(R.id.fabStopNavigation)
                fabStopNavigation.visibility = View.GONE
                
                Log.d("MainActivity", "Route cleared from map and tracking stopped")
                Toast.makeText(this@MainActivity, "Navigation stopped", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error clearing route from map", e)
            Toast.makeText(this, "Error clearing route", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePreferencesWidget(preferences: List<String>) {
        val preferencesContainer = findViewById<LinearLayout>(R.id.preferencesContainer)

        // Clear previous preference views
        preferencesContainer.removeAllViews()

        if (preferences.isEmpty()) {
            // Create and show "no preferences" message
            val tvNoPreferences = TextView(this)
            tvNoPreferences.apply {
                text = "Nessuna preferenza"
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ios_text_secondary))
                setPadding(0, 4, 0, 4)
            }
            preferencesContainer.addView(tvNoPreferences)
        } else {
            // Show up to 3 preferences to fit in the widget
            val maxPrefs = minOf(3, preferences.size)
            for (i in 0 until maxPrefs) {
                val prefView = TextView(this)
                prefView.apply {
                    text = "• ${preferences[i]}"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ios_text_primary))
                    setPadding(0, 4, 0, 4)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                preferencesContainer.addView(prefView)
            }
            
            // If there are more preferences, show "..." indicator
            if (preferences.size > 3) {
                val moreView = TextView(this)
                moreView.apply {
                    text = "...e altre ${preferences.size - 3}"
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ios_text_secondary))
                    setPadding(0, 4, 0, 0)
                }
                preferencesContainer.addView(moreView)
            }
        }
    }

    private fun startLocationTrackingForRoute() {
        if (!isNavigating || currentRouteCoordinates == null) return
        
        // Check location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.w("MainActivity", "Location permission not granted for route tracking")
            return
        }

        // Create location listener for progressive route updates
        locationListener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                try {
                    if (isNavigating && currentRouteCoordinates != null && location != null) {
                        // Check if location is valid
                        if (location.latitude != 0.0 && location.longitude != 0.0) {
                            updateRouteProgressively(location.latitude, location.longitude)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error in location change callback", e)
                }
            }

            @Deprecated("Deprecated in API level 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            // Request location updates for route tracking
            locationListener?.let { listener ->
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000, // Update every 3 seconds
                    8.0f, // Update every 8 meters
                    listener
                )
                
                // Also use network provider as backup
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000, // Update every 5 seconds
                    15.0f, // Update every 15 meters
                    listener
                )
            }
            
            Log.d("MainActivity", "Started location tracking for progressive route updates")
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Security exception starting location tracking", e)
        }
    }

    private fun updateRouteProgressively(userLat: Double, userLng: Double) {
        try {
            val userPoint = Point.fromLngLat(userLng, userLat)
            currentRouteCoordinates?.let { route ->
                // Find the closest point on the route to user's current location
                val closestIndex = findClosestPointOnRoute(route, userPoint)
                
                if (closestIndex > 0) {
                    // Create new route from closest point to destination (remove completed part)
                    val remainingRoute = route.subList(closestIndex, route.size)
                    
                    if (remainingRoute.size >= 2) {
                        // Update route coordinates and display with thread safety
                        currentRouteCoordinates = remainingRoute.toMutableList()
                        
                        // Throttle updates to prevent too frequent map changes
                        if (System.currentTimeMillis() - lastRouteUpdateTime > 2000) { // 2 seconds throttle
                            updateRouteDisplay(remainingRoute)
                            lastRouteUpdateTime = System.currentTimeMillis()
                            
                            Log.d("MainActivity", "Route updated: ${route.size - remainingRoute.size} points completed, ${remainingRoute.size} remaining")
                        }
                    } else {
                        // Route completed - destination reached
                        runOnUiThread {
                            stopNavigationTracking()
                            Toast.makeText(this@MainActivity, "Destination reached! 🎯", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in updateRouteProgressively", e)
            // Stop navigation on error to prevent crashes
            runOnUiThread {
                stopNavigationTracking()
                Toast.makeText(this@MainActivity, "Navigation error occurred", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun findClosestPointOnRoute(route: List<Point>, userLocation: Point): Int {
        var closestIndex = 0
        var minDistance = Double.MAX_VALUE

        route.forEachIndexed { index, point ->
            val distance = calculateDistanceBetweenPoints(
                userLocation.latitude(), userLocation.longitude(),
                point.latitude(), point.longitude()
            )
            
            // Consider points within threshold and keep track of the furthest completed point
            if (distance <= ROUTE_PROXIMITY_THRESHOLD && index > closestIndex) {
                minDistance = distance
                closestIndex = index
            }
        }

        return closestIndex
    }

    private fun calculateDistanceBetweenPoints(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        // Haversine formula for calculating distance between two points
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    private fun updateRouteDisplay(remainingRoute: List<Point>) {
        try {
            // Ensure we're on the main thread for map operations
            runOnUiThread {
                mapView.mapboxMap.getStyle { style ->
                    try {
                        if (style.styleSourceExists("route-source")) {
                            // Create new LineString with only the remaining route
                            val lineString = LineString.fromLngLats(remainingRoute)
                            
                            // Update route source with new geometry safely
                            val routeSource = geoJsonSource("route-source") {
                                geometry(lineString)
                            }
                            
                            // Safely remove and re-add source
                            if (style.styleLayerExists("route-layer")) {
                                style.removeStyleLayer("route-layer")
                            }
                            if (style.styleSourceExists("route-source")) {
                                style.removeStyleSource("route-source")
                            }
                            
                            // Add updated source and layer
                            style.addSource(routeSource)
                            val routeLayer = lineLayer("route-layer", "route-source") {
                                lineColor("#007AFF")
                                lineWidth(5.0)
                            }
                            style.addLayer(routeLayer)
                            
                            Log.d("MainActivity", "Route display updated safely - showing ${remainingRoute.size} remaining points")
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error updating route display", e)
                        // If update fails, stop navigation to prevent further crashes
                        stopNavigationTracking()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Critical error in updateRouteDisplay", e)
            stopNavigationTracking()
        }
    }

    private fun stopNavigationTracking() {
        isNavigating = false
        currentRouteCoordinates = null
        
        // Stop location updates
        locationListener?.let { listener ->
            try {
                locationManager.removeUpdates(listener)
                Log.d("MainActivity", "Stopped location tracking for route")
            } catch (e: SecurityException) {
                Log.e("MainActivity", "Error stopping location updates", e)
            }
        }
        locationListener = null
        
        // Keep the route visible but stop updating it
        Log.d("MainActivity", "Navigation tracking stopped - route remains visible")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up location tracking when activity is destroyed
        if (isNavigating) {
            stopNavigationTracking()
        }
    }
}
