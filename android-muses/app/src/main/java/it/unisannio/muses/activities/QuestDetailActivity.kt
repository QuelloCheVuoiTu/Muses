package it.unisannio.muses.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import it.unisannio.muses.R
import it.unisannio.muses.data.models.Quest
import it.unisannio.muses.data.models.Task
import it.unisannio.muses.data.repositories.QuestRepository
import it.unisannio.muses.utils.TextFormatUtils
import it.unisannio.muses.utils.ThemeManager
import kotlinx.coroutines.launch

class QuestDetailActivity : ComponentActivity() {

    companion object {
        const val EXTRA_QUEST_ID = "quest_id"
        const val EXTRA_QUEST_TITLE = "quest_title"
        const val EXTRA_MISSION_ID = "mission_id"
    }

    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d("QuestDetailActivity", "Task completed successfully via QR scanner")
            // Refresh the quest data to show updated task completion
            val questId = intent.getStringExtra(EXTRA_QUEST_ID)
            if (questId != null) {
                loadQuestDetails(questId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.initializeTheme(this)
        
        Log.d("QuestDetailActivity", "onCreate called")
        
        try {
            setContentView(R.layout.activity_quest_detail)
            Log.d("QuestDetailActivity", "Layout set successfully")

            val questId = intent.getStringExtra(EXTRA_QUEST_ID)
            val questTitle = intent.getStringExtra(EXTRA_QUEST_TITLE)
            val missionId = intent.getStringExtra(EXTRA_MISSION_ID)
            
            Log.d("QuestDetailActivity", "Intent data - Quest ID: $questId, Title: $questTitle, Mission ID: $missionId")

            if (questId == null || missionId == null) {
                Log.e("QuestDetailActivity", "Missing required parameters")
                finish()
                return
            }

            // Setup UI
            val titleText = findViewById<TextView>(R.id.textQuestDetailTitle)
            val descriptionText = findViewById<TextView>(R.id.textQuestDetailDescription)
            val statusText = findViewById<TextView>(R.id.textQuestDetailStatus)
            val progressText = findViewById<TextView>(R.id.textQuestProgress)
            val btnExpandDescription = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExpandDescription)
            val recycler = findViewById<RecyclerView>(R.id.recyclerTasks)
            
            Log.d("QuestDetailActivity", "UI elements found successfully")
            Log.d("QuestDetailActivity", "btnExpandDescription found: ${btnExpandDescription != null}")
            Log.d("QuestDetailActivity", "descriptionText found: ${descriptionText != null}")
            
            // Set initial title
            titleText.text = questTitle ?: "Loading..."
            descriptionText.text = "Loading quest details..."
            statusText.text = "Status: Loading..."
            progressText.text = "Progress: Loading..."
            
            // Configure RecyclerView for better performance in NestedScrollView
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.setHasFixedSize(false) // Allow dynamic height for expandable cards
            recycler.isNestedScrollingEnabled = false // Disable nested scrolling
            
            val adapter = TaskAdapter(questId, missionId) { taskId ->
                // Handle task click - open QR scanner
                val intent = Intent(this, QRScannerActivity::class.java)
                qrScannerLauncher.launch(intent)
            }
            recycler.adapter = adapter

            // Setup description expand/collapse functionality
            var isDescriptionExpanded = false
            Log.d("QuestDetailActivity", "Setting up expand button click listener")
            
            btnExpandDescription.setOnClickListener {
                Log.d("QuestDetailActivity", "Expand button clicked, current state: $isDescriptionExpanded")
                isDescriptionExpanded = !isDescriptionExpanded
                if (isDescriptionExpanded) {
                    // Expand description
                    descriptionText.maxLines = Int.MAX_VALUE
                    descriptionText.ellipsize = null
                    btnExpandDescription.text = "Mostra meno"
                    btnExpandDescription.icon = ContextCompat.getDrawable(this@QuestDetailActivity, R.drawable.ic_arrow_back)
                    Log.d("QuestDetailActivity", "Description expanded")
                } else {
                    // Collapse description
                    descriptionText.maxLines = 2
                    descriptionText.ellipsize = android.text.TextUtils.TruncateAt.END
                    btnExpandDescription.text = "Leggi di più"
                    btnExpandDescription.icon = ContextCompat.getDrawable(this@QuestDetailActivity, R.drawable.ic_arrow_forward)
                    Log.d("QuestDetailActivity", "Description collapsed")
                }
            }

            Log.d("QuestDetailActivity", "Starting to load quest details")

            // Load quest details
            loadQuestDetails(questId)
        } catch (e: Exception) {
            Log.e("QuestDetailActivity", "Error in onCreate", e)
            finish()
        }
    }

    private fun loadQuestDetails(questId: String) {
        val questRepository = QuestRepository()
        lifecycleScope.launch {
            try {
                Log.d("QuestDetailActivity", "Requesting quest with ID: $questId")
                val response = questRepository.getQuestById(questId)
                
                if (response.isSuccessful) {
                    val quest = response.body()
                    if (quest != null) {
                        Log.d("QuestDetailActivity", "Quest loaded successfully: ${quest.title}")
                        
                        // Update UI with quest details
                        val titleText = findViewById<TextView>(R.id.textQuestDetailTitle)
                        val descriptionText = findViewById<TextView>(R.id.textQuestDetailDescription)
                        val statusText = findViewById<TextView>(R.id.textQuestDetailStatus)
                        val progressText = findViewById<TextView>(R.id.textQuestProgress)
                        val btnExpandDescription = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExpandDescription)
                        
                        titleText.text = TextFormatUtils.formatTitle(quest.title ?: "Quest senza titolo")
                        val description = TextFormatUtils.formatDescription(quest.description ?: "Nessuna descrizione disponibile")
                        descriptionText.text = description
                        statusText.text = "Stato: ${quest.status}"
                        
                        // Set description as collapsed initially and show expand button
                        descriptionText.maxLines = 2
                        descriptionText.ellipsize = android.text.TextUtils.TruncateAt.END
                        
                        // Check if description is long enough to need expand button
                        if (description.length > 100) { // Simple character count check
                            btnExpandDescription.visibility = View.VISIBLE
                            Log.d("QuestDetailActivity", "Description is long (${description.length} chars), showing expand button")
                        } else {
                            btnExpandDescription.visibility = View.GONE
                            descriptionText.maxLines = Int.MAX_VALUE
                            descriptionText.ellipsize = null
                            Log.d("QuestDetailActivity", "Description is short (${description.length} chars), hiding expand button")
                        }
                        
                        // Convert tasks map to list for the adapter
                        val tasksList = quest.tasks.map { (taskId, task) ->
                            TaskWithId(taskId, task)
                        }
                        
                        val completedTasks = tasksList.count { it.task.completed }
                        progressText.text = "Progress: $completedTasks/${tasksList.size} tasks completed"
                        
                        val recycler = findViewById<RecyclerView>(R.id.recyclerTasks)
                        (recycler.adapter as? TaskAdapter)?.setItems(tasksList)
                        
                        Log.d("QuestDetailActivity", "UI updated with ${tasksList.size} tasks")
                        Log.d("QuestDetailActivity", "Tasks details: ${tasksList.map { "${it.taskId}: ${it.task.title}" }}")
                        
                        // Force RecyclerView to measure properly
                        recycler.post {
                            recycler.requestLayout()
                        }
                    } else {
                        Log.e("QuestDetailActivity", "Quest response body is null")
                        handleLoadError("Failed to load quest details.")
                    }
                } else {
                    Log.e("QuestDetailActivity", "Failed to load quest: ${response.code()}")
                    handleLoadError("Failed to load quest details (HTTP ${response.code()})")
                }
            } catch (e: Exception) {
                Log.e("QuestDetailActivity", "Error loading quest details", e)
                handleLoadError("Error loading quest: ${e.message}")
            }
        }
    }

    private fun handleLoadError(message: String) {
        val titleText = findViewById<TextView>(R.id.textQuestDetailTitle)
        val descriptionText = findViewById<TextView>(R.id.textQuestDetailDescription)
        val statusText = findViewById<TextView>(R.id.textQuestDetailStatus)
        val progressText = findViewById<TextView>(R.id.textQuestProgress)
        val btnExpandDescription = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExpandDescription)
        
        titleText.text = intent.getStringExtra(EXTRA_QUEST_TITLE) ?: "Error"
        descriptionText.text = message
        statusText.text = "Status: Error"
        progressText.text = "Unable to load progress"
        
        // Hide expand button on error
        btnExpandDescription.visibility = View.GONE
        descriptionText.maxLines = Int.MAX_VALUE
        descriptionText.ellipsize = null
    }

    data class TaskWithId(
        val taskId: String,
        val task: Task
    )

    private class TaskAdapter(
        private val questId: String,
        private val missionId: String,
        private val onTaskClick: (String) -> Unit
    ) : RecyclerView.Adapter<TaskAdapter.TaskVH>() {
        private val items = mutableListOf<TaskWithId>()

        fun setItems(newItems: List<TaskWithId>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
            Log.d("TaskAdapter", "Set ${newItems.size} items in adapter")
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
            return TaskVH(v)
        }

        override fun onBindViewHolder(holder: TaskVH, position: Int) {
            holder.bind(items[position], onTaskClick)
        }

        override fun getItemCount(): Int = items.size

        class TaskVH(view: View) : RecyclerView.ViewHolder(view) {
            private val titleText = view.findViewById<TextView>(R.id.textTaskTitle)
            private val descriptionText = view.findViewById<TextView>(R.id.textTaskDescription)
            private val statusText = view.findViewById<TextView>(R.id.textTaskStatus)
            private val layoutQRButton = view.findViewById<LinearLayout>(R.id.layoutQRButton)
            private val btnScanQR = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnScanQR)
            private var isExpanded = false

            fun bind(taskWithId: TaskWithId, onTaskClick: (String) -> Unit) {
                val task = taskWithId.task
                
                val taskTitleString = task.title?.takeIf { it.isNotEmpty() } ?: "Task ${taskWithId.taskId}"
                val taskDescriptionString = task.description?.takeIf { it.isNotEmpty() } ?: "Nessuna descrizione disponibile"
                
                titleText.text = TextFormatUtils.formatTitle(taskTitleString)
                descriptionText.text = TextFormatUtils.formatDescription(taskDescriptionString)
                statusText.text = if (task.completed) "✓ COMPLETATO" else "○ IN ATTESA"
                
                // Change colors based on completion status
                val statusColor = if (task.completed) 
                    android.graphics.Color.parseColor("#4CAF50") // Green
                else 
                    androidx.core.content.ContextCompat.getColor(itemView.context, R.color.secondary_text)
                    
                val titleColor = if (task.completed)
                    android.graphics.Color.parseColor("#4CAF50") // Green for completed
                else
                    androidx.core.content.ContextCompat.getColor(itemView.context, R.color.primary_text)
                    
                statusText.setTextColor(statusColor)
                titleText.setTextColor(titleColor)
                
                // Reset expansion state
                isExpanded = false
                descriptionText.maxLines = 2
                descriptionText.ellipsize = android.text.TextUtils.TruncateAt.END
                layoutQRButton.visibility = View.GONE
                
                // Set click listener for expanding card
                itemView.setOnClickListener {
                    toggleExpansion(task, taskWithId.taskId, onTaskClick)
                }
                
                // Make it visually clickable
                itemView.isClickable = true
                itemView.isFocusable = true
                itemView.background = itemView.context.getDrawable(R.drawable.card_background)
                
                // Set QR button click listener only for uncompleted tasks
                if (!task.completed) {
                    btnScanQR.setOnClickListener {
                        onTaskClick(taskWithId.taskId)
                    }
                } else {
                    btnScanQR.setOnClickListener(null)
                }
            }
            
            private fun toggleExpansion(task: Task, taskId: String, onTaskClick: (String) -> Unit) {
                isExpanded = !isExpanded
                
                if (isExpanded) {
                    // Expand card
                    descriptionText.maxLines = Int.MAX_VALUE
                    descriptionText.ellipsize = null
                    
                    // Show QR button only for uncompleted tasks
                    if (!task.completed) {
                        layoutQRButton.visibility = View.VISIBLE
                    }
                    
                    Log.d("TaskAdapter", "Expanded task: $taskId")
                } else {
                    // Collapse card
                    descriptionText.maxLines = 2
                    descriptionText.ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutQRButton.visibility = View.GONE
                    
                    Log.d("TaskAdapter", "Collapsed task: $taskId")
                }
            }
        }
    }
}
