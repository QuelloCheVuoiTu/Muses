package it.unisannio.muses.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import it.unisannio.muses.R
import it.unisannio.muses.data.models.Mission
import it.unisannio.muses.data.models.MissionStep
import it.unisannio.muses.data.repositories.MissionRepository
import it.unisannio.muses.data.repositories.QuestRepository
import it.unisannio.muses.helpers.AuthTokenManager
import it.unisannio.muses.utils.TextFormatUtils
import it.unisannio.muses.utils.ThemeManager
import kotlinx.coroutines.launch

class MissionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Inizializza il tema prima di tutto
        ThemeManager.initializeTheme(this)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_missions)

        val recycler = findViewById<RecyclerView>(R.id.recyclerMissions)
        recycler.layoutManager = LinearLayoutManager(this)
        val adapter = MissionAdapter()
        recycler.adapter = adapter

        // Get user id from saved auth token manager
        val authTokenManager = AuthTokenManager(this)
        val userId = authTokenManager.getEntityId() ?: run {
            Log.e("MissionActivity", "No user id available")
            return
        }

        val repo = MissionRepository()
        lifecycleScope.launch {
            try {
                val response = repo.getUserMissions(userId)
                Log.d("MissionActivity", "Response code: ${response.code()}")
                if (response.isSuccessful) {
                    val missions = response.body() ?: emptyList<Mission>()
                    Log.d("MissionActivity", "Missions loaded: ${missions.size}")
                    missions.forEach { mission ->
                        Log.d("MissionActivity", "Mission: id=${mission.id}, status=${mission.status}, steps=${mission.steps.size}")
                    }
                    adapter.setItems(missions)
                } else {
                    Log.e("MissionActivity", "Failed to load missions: ${response.code()}")
                    val errorBodyString = response.errorBody()?.string()
                    Log.e("MissionActivity", "Error body: $errorBodyString")
                    adapter.setErrorMessage("Failed to load missions. Server returned: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("MissionActivity", "Error loading missions", e)
                adapter.setErrorMessage("Error loading missions: ${e.message}")
            }
        }
    }

    private class MissionAdapter : RecyclerView.Adapter<MissionAdapter.MissionVH>() {
        private val items = mutableListOf<Mission>()
        private var errorMessage: String? = null

        fun setItems(newItems: List<Mission>) {
            items.clear()
            items.addAll(newItems)
            errorMessage = null
            notifyDataSetChanged()
        }
        
        fun setErrorMessage(message: String) {
            items.clear()
            errorMessage = message
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MissionVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_mission, parent, false)
            return MissionVH(v)
        }

        override fun onBindViewHolder(holder: MissionVH, position: Int) {
            when {
                errorMessage != null -> {
                    holder.bindError(errorMessage!!)
                }
                items.isEmpty() -> {
                    holder.bindNoMissions()
                }
                else -> {
                    holder.bind(items[position]) { mission ->
                        // Handle mission click - open QuestActivity
                        val context = holder.itemView.context
                        Log.d("MissionActivity", "Mission clicked: ${mission.id}, steps: ${mission.steps.size}")
                        
                        try {
                            val stepIds = mission.steps.map { it.stepId }.toTypedArray()
                            val stepCompleted = mission.steps.map { it.completed }.toBooleanArray()
                            
                            val intent = Intent(context, QuestActivity::class.java).apply {
                                putExtra(QuestActivity.EXTRA_MISSION_ID, mission.id)
                                putExtra(QuestActivity.EXTRA_MISSION_STATUS, mission.status)
                                putExtra(QuestActivity.EXTRA_STEP_IDS, stepIds)
                                putExtra(QuestActivity.EXTRA_STEP_COMPLETED, stepCompleted)
                            }
                            Log.d("MissionActivity", "Starting QuestActivity with ${stepIds.size} steps")
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("MissionActivity", "Error starting QuestActivity", e)
                        }
                    }
                }
            }
        }

        override fun getItemCount(): Int = when {
            errorMessage != null -> 1
            items.isEmpty() -> 1
            else -> items.size
        }

        class MissionVH(view: View) : RecyclerView.ViewHolder(view) {
            private val textTitle = view.findViewById<TextView>(R.id.textMissionTitle)
            private val textStatus = view.findViewById<TextView>(R.id.textMissionStatus)
            private val textDescription = view.findViewById<TextView>(R.id.textMissionDescription)
            
            fun bind(mission: Mission, onClickListener: (Mission) -> Unit) {
                // Inizialmente mostra l'ID, poi carica il titolo della prima quest
                textTitle.text = "Mission ${mission.id.takeLast(8)}"
                
                // Carica il titolo della prima quest in modo asincrono
                loadFirstQuestTitle(mission)
                
                // Show the exact mission status from the mission data
                textStatus.text = mission.status.uppercase()
                
                // Colora lo status basandosi sullo status reale
                val statusColor = when (mission.status.uppercase()) {
                    "COMPLETE" -> android.graphics.Color.parseColor("#4CAF50") // Verde
                    "IN_PROGRESS" -> android.graphics.Color.parseColor("#2196F3") // Blu  
                    "PENDING" -> android.graphics.Color.parseColor("#FF9800") // Arancione
                    "STOPPED" -> android.graphics.Color.parseColor("#F44336") // Rosso
                    "ACTIVE", "STARTED" -> android.graphics.Color.parseColor("#4CAF50") // Verde per compatibilità
                    else -> android.graphics.Color.parseColor("#757575") // Grigio per status sconosciuti
                }
                textStatus.setTextColor(statusColor)
                
                // Crea una descrizione basata sul progresso
                val completedSteps = mission.steps.count { it.completed }
                val totalSteps = mission.steps.size
                val progressText = if (totalSteps > 0) {
                    val percentage = (completedSteps * 100) / totalSteps
                    "Progresso: $completedSteps di $totalSteps quest completate ($percentage%)"
                } else {
                    "Nessuna quest disponibile"
                }
                textDescription.text = progressText
                
                itemView.setOnClickListener { onClickListener(mission) }
            }
            
            fun bindError(message: String) {
                textTitle.text = "Errore"
                textStatus.text = ""
                textDescription.text = message
                itemView.setOnClickListener(null)
            }
            
            fun bindNoMissions() {
                textTitle.text = "Nessuna Missione"
                textStatus.text = "○ VUOTO"
                textStatus.setTextColor(android.graphics.Color.parseColor("#666666")) // Grigio
                textDescription.text = "Non ci sono missioni disponibili al momento."
                itemView.setOnClickListener(null)
            }
            
            private fun loadFirstQuestTitle(mission: Mission) {
                // Se non ci sono steps, mantieni il titolo attuale
                if (mission.steps.isEmpty()) return
                
                // Prendi il primo step (prima quest)
                val firstStep = mission.steps.first()
                val questId = firstStep.stepId
                
                // Carica la quest in background
                val context = itemView.context
                if (context is ComponentActivity) {
                    context.lifecycleScope.launch {
                        try {
                            val questRepository = QuestRepository()
                            val response = questRepository.getQuestById(questId)
                            
                            if (response.isSuccessful) {
                                val quest = response.body()
                                if (quest != null) {
                                    val questTitle = TextFormatUtils.formatTitle(
                                        quest.title?.takeIf { it.isNotEmpty() } ?: "Mission ${mission.id.takeLast(8)}"
                                    )
                                    // Aggiorna il titolo nella UI thread
                                    textTitle.text = questTitle
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MissionActivity", "Error loading quest title for mission ${mission.id}", e)
                            // In caso di errore, mantieni il titolo originale
                        }
                    }
                }
            }
        }
    }
}
