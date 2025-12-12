package it.unisannio.muses.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import it.unisannio.muses.R
import it.unisannio.muses.data.models.Mission
import it.unisannio.muses.data.models.MissionStep
import it.unisannio.muses.data.models.Quest
import it.unisannio.muses.data.models.Museum
import it.unisannio.muses.data.repositories.QuestRepository
import it.unisannio.muses.data.repositories.MissionRepository
import it.unisannio.muses.data.repositories.MuseumRepository
import it.unisannio.muses.data.repositories.NavigationRepository
import it.unisannio.muses.helpers.AuthTokenManager
import it.unisannio.muses.utils.TextFormatUtils
import it.unisannio.muses.utils.ThemeManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class QuestActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MISSION_ID = "mission_id"
        const val EXTRA_MISSION_STATUS = "mission_status"
        const val EXTRA_STEP_IDS = "step_ids"
        const val EXTRA_STEP_COMPLETED = "step_completed"
    }

    // UI components
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: android.widget.Button
    private lateinit var startNavigationButton: MaterialButton
    private lateinit var adapter: QuestAdapter
    
    // Data
    private lateinit var missionId: String
    private var currentMissionStatus: String = "UNKNOWN"
    private var missionSteps: List<MissionStep> = emptyList()
    private var inProgressQuest: Quest? = null
    private val museumRepository = MuseumRepository()
    private val navigationRepository = NavigationRepository()
    
    // Location permission request
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted, retry navigation
                startNavigationToInProgressQuest()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted, retry navigation
                startNavigationToInProgressQuest()
            }
            else -> {
                // No location access granted
                Toast.makeText(this, "Location permission is required for navigation", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.initializeTheme(this)
        
        Log.d("QuestActivity", "onCreate called")
        
        try {
            setContentView(R.layout.activity_quest)
            Log.d("QuestActivity", "Layout set successfully")

            val receivedMissionId = intent.getStringExtra(EXTRA_MISSION_ID)
            val receivedMissionStatus = intent.getStringExtra(EXTRA_MISSION_STATUS)
            val stepIds = intent.getStringArrayExtra(EXTRA_STEP_IDS)
            val stepCompleted = intent.getBooleanArrayExtra(EXTRA_STEP_COMPLETED)
            
            Log.d("QuestActivity", "Intent data - Mission ID: $receivedMissionId, Status: $receivedMissionStatus, Step IDs: ${stepIds?.contentToString()}")

            if (receivedMissionId == null || stepIds == null || stepCompleted == null) {
                Log.e("QuestActivity", "Missing required intent data")
                finish()
                return
            }
            
            // Initialize properties
            this.missionId = receivedMissionId
            this.currentMissionStatus = receivedMissionStatus ?: "UNKNOWN"

            if (stepIds.size != stepCompleted.size) {
                Log.e("QuestActivity", "Step IDs and completed arrays have different sizes")
                finish()
                return
            }

            val finalStatus = currentMissionStatus
            
            // Create MissionStep objects from arrays
            val steps = stepIds.mapIndexed { index, stepId ->
                MissionStep(stepCompleted[index], stepId)
            }
            
            // Salva gli step come variabile di istanza
            this.missionSteps = steps

            // Setup UI
            titleText = findViewById<TextView>(R.id.textMissionTitle)
            statusText = findViewById<TextView>(R.id.textMissionStatus)
            startButton = findViewById<android.widget.Button>(R.id.btnStartMission)
            startNavigationButton = findViewById<MaterialButton>(R.id.btnStartNavigation)
            val recycler = findViewById<RecyclerView>(R.id.recyclerQuests)
            
            Log.d("QuestActivity", "UI elements found successfully")
            
            // Initialize UI
            updateUI()
            
            // Setup start mission button click
            setupStartButton()
            
            // Setup start navigation button click
            setupNavigationButton()
            
            recycler.layoutManager = LinearLayoutManager(this)
            adapter = QuestAdapter(missionId)
            recycler.adapter = adapter

            // Load quest details
            loadQuestDetails(steps)
        } catch (e: Exception) {
            Log.e("QuestActivity", "Error in onCreate", e)
            finish()
        }
    }

    private fun updateUI() {
        // Inizialmente mostra l'ID, poi carica il titolo della prima quest
        titleText.text = "Mission: $missionId"
        statusText.text = "Status: $currentMissionStatus"
        
        // Carica il titolo della prima quest se disponibile
        loadFirstQuestTitleForActivity()
        
        updateStartButton()
    }
    
    private fun loadFirstQuestTitleForActivity() {
        // Se non ci sono steps, mantieni il titolo attuale
        if (missionSteps.isEmpty()) return
        
        // Prendi il primo step (prima quest)
        val firstStep = missionSteps.first()
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
                            quest.title?.takeIf { it.isNotEmpty() } ?: "Mission: $missionId"
                        )
                        // Aggiorna il titolo nella UI thread
                        titleText.text = questTitle
                    }
                }
            } catch (e: Exception) {
                Log.e("QuestActivity", "Error loading quest title for mission $missionId", e)
                // In caso di errore, mantieni il titolo originale
            }
        }
    }

    private fun updateStartButton() {
        when (currentMissionStatus.uppercase()) {
            "PENDING" -> {
                startButton.isEnabled = true
                startButton.text = "START MISSION"
                startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#4CAF50")
                )
            }
            "IN_PROGRESS" -> {
                startButton.isEnabled = false
                startButton.text = "MISSION IN PROGRESS"
                startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#FF9800")
                )
            }
            "COMPLETED" -> {
                startButton.isEnabled = false
                startButton.text = "MISSION COMPLETED ✓"
                startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#2E7D32")
                )
            }
            else -> {
                startButton.isEnabled = false
                startButton.text = "UNKNOWN STATUS"
                startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#757575")
                )
            }
        }
    }

    private fun setupStartButton() {
        startButton.setOnClickListener {
            if (currentMissionStatus.uppercase() != "PENDING") {
                Log.d("QuestActivity", "Start button clicked but mission is not PENDING: $currentMissionStatus")
                return@setOnClickListener
            }
            
            Log.d("QuestActivity", "Start button clicked for mission: $missionId")
            
            // Disable button to prevent multiple clicks
            startButton.isEnabled = false
            startButton.text = "STARTING..."
            
            // Start mission using coroutine
            lifecycleScope.launch {
                try {
                    val missionRepository = MissionRepository()
                    val response = missionRepository.startMission(missionId)
                    
                    if (response.isSuccessful) {
                        Log.d("QuestActivity", "Mission started successfully: $missionId")
                        // Refresh mission data to get updated status
                        refreshMissionData()
                    } else {
                        Log.e("QuestActivity", "Failed to start mission: $missionId")
                        startButton.text = "ERROR - TRY AGAIN"
                        startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#F44336")
                        )
                        // Re-enable after error
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            updateStartButton()
                        }, 2000)
                    }
                } catch (e: Exception) {
                    Log.e("QuestActivity", "Exception starting mission: $missionId", e)
                    startButton.text = "ERROR - TRY AGAIN"
                    startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#F44336")
                    )
                    // Re-enable after error
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        updateStartButton()
                    }, 2000)
                }
            }
        }
    }
    
    private fun setupNavigationButton() {
        startNavigationButton.setOnClickListener {
            startNavigationToInProgressQuest()
        }
    }
    
    private fun startNavigationToInProgressQuest() {
        val quest = inProgressQuest
        if (quest == null) {
            Toast.makeText(this, "No quest in progress found", Toast.LENGTH_SHORT).show()
            return
        }
        
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
        
        // Load museum information and start navigation
        lifecycleScope.launch {
            try {
                val museumResponse = museumRepository.getMuseumById(quest.subjectId)
                if (museumResponse.isSuccessful) {
                    val museum = museumResponse.body()
                    if (museum != null) {
                        navigateToMuseum(museum, lastKnownLocation)
                    } else {
                        Toast.makeText(this@QuestActivity, "Museum not found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@QuestActivity, "Failed to load museum information", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("QuestActivity", "Error loading museum for navigation", e)
                Toast.makeText(this@QuestActivity, "Error loading museum information", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun navigateToMuseum(museum: Museum, location: android.location.Location) {
        val userLat = location.latitude
        val userLong = location.longitude
        val museumLat = museum.location.latitude
        val museumLong = museum.location.longitude

        // Show loading indicator
        Toast.makeText(this, "Calculating route to ${museum.name}...", Toast.LENGTH_SHORT).show()

        // Fetch route from OSRM API
        lifecycleScope.launch {
            try {
                val response = navigationRepository.getRoute(userLong, userLat, museumLong, museumLat)
                
                if (response.isSuccessful) {
                    val osrmResponse = response.body()
                    if (osrmResponse != null && osrmResponse.routes.isNotEmpty()) {
                        val route = osrmResponse.routes[0]
                        
                        // Show route info and finish activity to return to main
                        val distanceKm = route.distance / 1000
                        val durationMin = route.duration / 60
                        
                        Toast.makeText(
                            this@QuestActivity,
                            String.format("Route found: %.1f km, %.0f minutes walking to ${museum.name}", distanceKm, durationMin),
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Launch MainActivity with navigation data
                        val intent = Intent(this@QuestActivity, it.unisannio.muses.MainActivity::class.java).apply {
                            putExtra("NAVIGATION_POLYLINE", route.geometry)
                            putExtra("NAVIGATION_MUSEUM_NAME", museum.name)
                            putExtra("NAVIGATION_DISTANCE", distanceKm)
                            putExtra("NAVIGATION_DURATION", durationMin)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                        finish()
                        
                        Log.d("QuestActivity", "Route calculated successfully: ${distanceKm}km, ${durationMin}min")
                    } else {
                        Toast.makeText(this@QuestActivity, "No route found to ${museum.name}", Toast.LENGTH_SHORT).show()
                        Log.e("QuestActivity", "No routes in response")
                    }
                } else {
                    Toast.makeText(this@QuestActivity, "Failed to calculate route", Toast.LENGTH_SHORT).show()
                    Log.e("QuestActivity", "Route API error. Code: ${response.code()}")
                }
            } catch (e: Exception) {
                Toast.makeText(this@QuestActivity, "Network error. Please check your connection.", Toast.LENGTH_SHORT).show()
                Log.e("QuestActivity", "Exception during route request", e)
            }
        }
    }

    private fun refreshMissionData() {
        lifecycleScope.launch {
            try {
                val authTokenManager = AuthTokenManager(this@QuestActivity)
                val userId = authTokenManager.getEntityId()
                
                if (userId != null) {
                    val missionRepository = MissionRepository()
                    val response = missionRepository.getUserMissions(userId)
                    
                    if (response.isSuccessful) {
                        val missions = response.body()
                        val currentMission = missions?.find { it.id == missionId }
                        
                        if (currentMission != null) {
                            Log.d("QuestActivity", "Mission refreshed - New status: ${currentMission.status}")
                            currentMissionStatus = currentMission.status
                            updateUI()
                            
                            // Reload quest details with updated completion status
                            val steps = currentMission.steps
                            loadQuestDetails(steps)
                        } else {
                            Log.w("QuestActivity", "Mission not found in response: $missionId")
                        }
                    } else {
                        Log.e("QuestActivity", "Failed to refresh mission data: ${response.code()}")
                    }
                } else {
                    Log.e("QuestActivity", "User ID not available for refresh")
                }
            } catch (e: Exception) {
                Log.e("QuestActivity", "Exception refreshing mission data", e)
            }
        }
    }

    private fun loadQuestDetails(steps: List<MissionStep>) {
        Log.d("QuestActivity", "Starting to load quest details for ${steps.size} steps")

        // Load quest details for each step
        val questRepository = QuestRepository()
        lifecycleScope.launch {
            try {
                val questsWithDetails = mutableListOf<QuestWithStatus>()
                var foundInProgressQuest: Quest? = null
                
                for (step in steps) {
                    Log.d("QuestActivity", "Loading quest for step: ${step.stepId}")
                    try {
                        val response = questRepository.getQuestById(step.stepId)
                        if (response.isSuccessful) {
                            val quest = response.body()
                            if (quest != null) {
                                Log.d("QuestActivity", "Quest loaded successfully: ${quest.title ?: "No title"} - Status: ${quest.status}")
                                
                                // Check if this quest is IN_PROGRESS
                                if (quest.status.equals("IN_PROGRESS", ignoreCase = true)) {
                                    foundInProgressQuest = quest
                                }
                                
                                // Use quest status instead of mission step completion
                                // We'll show the real quest status in the UI
                                val questCompleted = quest.status.equals("COMPLETE", ignoreCase = true)
                                questsWithDetails.add(QuestWithStatus(quest, questCompleted))
                            } else {
                                Log.w("QuestActivity", "Quest response body is null for step: ${step.stepId} - Skipping quest (no real data)")
                                // Skip quest - only show quests with real data
                            }
                        } else {
                            Log.e("QuestActivity", "Failed to load quest ${step.stepId}: ${response.code()} - Skipping quest")
                            // Skip quest - only show quests with real data
                        }
                    } catch (e: Exception) {
                        Log.e("QuestActivity", "Exception loading quest ${step.stepId}: ${e.message} - Skipping quest")
                        // Skip quest - only show quests with real data
                    }
                }
                
                // Update in progress quest and navigation button visibility
                inProgressQuest = foundInProgressQuest
                updateNavigationButtonVisibility()
                
                Log.d("QuestActivity", "Setting ${questsWithDetails.size} quests to adapter")
                adapter.setItems(questsWithDetails)
            } catch (e: Exception) {
                Log.e("QuestActivity", "Error in loadQuestDetails", e)
            }
        }
    }
    
    private fun updateNavigationButtonVisibility() {
        runOnUiThread {
            if (inProgressQuest != null) {
                startNavigationButton.visibility = View.VISIBLE
                Log.d("QuestActivity", "Showing navigation button for quest: ${inProgressQuest?.title}")
            } else {
                startNavigationButton.visibility = View.GONE
                Log.d("QuestActivity", "Hiding navigation button - no IN_PROGRESS quest found")
            }
        }
    }

    data class QuestWithStatus(
        val quest: Quest,
        val completed: Boolean
    )

    private class QuestAdapter(private val missionId: String) : RecyclerView.Adapter<QuestAdapter.QuestVH>() {
        private val items = mutableListOf<QuestWithStatus>()

        fun setItems(newItems: List<QuestWithStatus>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_quest, parent, false)
            return QuestVH(v)
        }

        override fun onBindViewHolder(holder: QuestVH, position: Int) {
            holder.bind(items[position]) { questWithStatus ->
                // Handle quest click - open QuestDetailActivity
                val context = holder.itemView.context
                val quest = questWithStatus.quest
                Log.d("QuestActivity", "Quest clicked: ${quest.title} (ID: ${quest.id})")
                
                try {
                    val intent = Intent(context, QuestDetailActivity::class.java).apply {
                        putExtra(QuestDetailActivity.EXTRA_QUEST_ID, quest.id)
                        putExtra(QuestDetailActivity.EXTRA_QUEST_TITLE, quest.title)
                        putExtra(QuestDetailActivity.EXTRA_MISSION_ID, missionId)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("QuestActivity", "Error starting QuestDetailActivity", e)
                }
            }
        }

        override fun getItemCount(): Int = items.size

        class QuestVH(view: View) : RecyclerView.ViewHolder(view) {
            private val titleText = view.findViewById<TextView>(R.id.textQuestTitle)
            private val descriptionText = view.findViewById<TextView>(R.id.textQuestDescription)
            private val statusText = view.findViewById<TextView>(R.id.textQuestStatus)

            fun bind(questWithStatus: QuestWithStatus, onClickListener: (QuestWithStatus) -> Unit) {
                val quest = questWithStatus.quest
                val completed = questWithStatus.completed
                
                // Estrai il testo dal JSON nei campi title e description
                val titleString = quest.title?.takeIf { it.isNotEmpty() } ?: "Quest ${quest.id}"
                val descriptionString = quest.description?.takeIf { it.isNotEmpty() } ?: "Nessuna descrizione disponibile"
                
                titleText.text = TextFormatUtils.formatTitle(titleString)
                descriptionText.text = TextFormatUtils.formatDescription(descriptionString)
                
                // Show the actual quest status from the quest data
                statusText.text = quest.status.uppercase()
                
                // Change text color based on the actual quest status
                val statusColor = when (quest.status.uppercase()) {
                    "COMPLETE" -> android.graphics.Color.parseColor("#4CAF50") // Green
                    "IN_PROGRESS" -> android.graphics.Color.parseColor("#2196F3") // Blue  
                    "PENDING" -> android.graphics.Color.parseColor("#FF9800") // Orange
                    "STOPPED" -> android.graphics.Color.parseColor("#F44336") // Red
                    else -> android.graphics.Color.parseColor("#757575") // Gray for unknown status
                }
                    
                val titleColor = when (quest.status.uppercase()) {
                    "COMPLETE" -> android.graphics.Color.parseColor("#2E7D32") // Dark green
                    "IN_PROGRESS" -> android.graphics.Color.parseColor("#1976D2") // Dark blue
                    "PENDING" -> android.graphics.Color.parseColor("#F57C00") // Dark orange
                    "STOPPED" -> android.graphics.Color.parseColor("#D32F2F") // Dark red
                    else -> android.graphics.Color.parseColor("#424242") // Dark gray
                }
                    
                statusText.setTextColor(statusColor)
                titleText.setTextColor(titleColor)
                
                // Set quest click listener (for navigation to quest details)
                itemView.setOnClickListener {
                    onClickListener(questWithStatus)
                }
            }
        }
    }
}
