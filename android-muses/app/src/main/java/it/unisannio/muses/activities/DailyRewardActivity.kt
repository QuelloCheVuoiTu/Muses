package it.unisannio.muses.activities

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.unisannio.muses.R
import it.unisannio.muses.utils.ThemeManager
import it.unisannio.muses.adapters.DailyRewardAdapter
import it.unisannio.muses.data.models.DailyReward
import java.text.SimpleDateFormat
import java.util.*

class DailyRewardActivity : ComponentActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DailyRewardAdapter
    private val rewards = mutableListOf<DailyReward>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.initializeTheme(this)
        setContentView(R.layout.activity_daily_reward)

        initViews()
        setupRecyclerView()
        setupWeeklyRewards()
        updateProgress()
    }
    
    private fun initViews() {
        // Back button
        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            finish()
        }
        
        // Special reward button
        findViewById<Button>(R.id.btnSpecialReward).setOnClickListener {
            val button = it as Button
            if (button.isEnabled) {
                Toast.makeText(this, "Ricompensa speciale riscossa! 🎁", Toast.LENGTH_SHORT).show()
                button.isEnabled = false
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.rvWeeklyRewards)
        adapter = DailyRewardAdapter(rewards) { reward ->
            onRewardClicked(reward)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupWeeklyRewards() {
        val calendar = Calendar.getInstance()
        val todayDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        // Reset to start of week (Monday)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        
        val dayNames = arrayOf(
            "Lunedì", "Martedì", "Mercoledì", "Giovedì", 
            "Venerdì", "Sabato", "Domenica"
        )
        
        val rewardTypes = arrayOf(
            "quest", "discount", "gadget", "discount",
            "gadget", "discount", "special"
        )
        
        val rewardDescriptions = arrayOf(
            "Quest Evento",
            "Sconto Biglietto 20%", 
            "Badge Collezionista",
            "Sconto Souvenir 15%",
            "Avatar Speciale",
            "Sconto Caffetteria 25%",
            "Accesso VIP Museo"
        )
        
        val rewardValues = arrayOf(1, 20, 1, 15, 2, 25, 1)
        val rewardIcons = arrayOf(
            R.drawable.ic_missions, R.drawable.ic_discount, R.drawable.ic_trophy, R.drawable.ic_discount,
            R.drawable.ic_profile, R.drawable.ic_discount, R.drawable.ic_gift
        )

        rewards.clear()
        
        for (i in 0..6) {
            val dayCalendar = calendar.clone() as Calendar
            dayCalendar.add(Calendar.DAY_OF_WEEK, i)
            
            val dayNumber = dayCalendar.get(Calendar.DAY_OF_MONTH)
            val currentDayOfWeek = dayCalendar.get(Calendar.DAY_OF_WEEK)
            
            // Ogni ricompensa è disponibile solo nel suo giorno specifico
            val isToday = currentDayOfWeek == todayDayOfWeek
            val isAvailable = isToday
            
            // Nessun premio è già riscattato di default - l'utente deve riscattarli manualmente
            val isClaimed = false
            
            val reward = DailyReward(
                id = i + 1,
                dayName = dayNames[i],
                dayNumber = dayNumber,
                rewardType = rewardTypes[i],
                rewardValue = rewardValues[i],
                rewardDescription = rewardDescriptions[i],
                iconResource = rewardIcons[i],
                isAvailable = isAvailable,
                isClaimed = isClaimed,
                isToday = isToday
            )
            
            rewards.add(reward)
            
            // Log per debug
            Log.d("DailyReward", "Day ${dayNames[i]} - isToday: $isToday, isAvailable: $isAvailable, dayOfWeek: $currentDayOfWeek, today: $todayDayOfWeek")
        }
        
        adapter.notifyDataSetChanged()
    }

    private fun onRewardClicked(reward: DailyReward) {
        when {
            reward.isClaimed -> {
                Toast.makeText(this, "Hai già riscattato questo premio!", Toast.LENGTH_SHORT).show()
            }
            !reward.isAvailable -> {
                Toast.makeText(this, "Premio non ancora disponibile", Toast.LENGTH_SHORT).show()
            }
            !reward.isToday -> {
                Toast.makeText(this, "Puoi riscattare solo la ricompensa di oggi!", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // Claim the reward
                reward.isClaimed = true
                adapter.notifyDataSetChanged()
                
                // Ogni ricompensa vale sempre 1000 punti base + il bonus specifico
                val totalPoints = reward.basePoints
                
                val message = when (reward.rewardType) {
                    "Punti Bonus" -> "Hai guadagnato $totalPoints punti + ${reward.rewardValue} bonus!"
                    "Esperienza Extra" -> "Hai guadagnato $totalPoints punti + ${reward.rewardValue} esperienza!"
                    "Biglietto Gratis" -> "Hai guadagnato $totalPoints punti + biglietto gratuito!"
                    "Sconto 50%" -> "Hai guadagnato $totalPoints punti + sconto del ${reward.rewardValue}%!"
                    "Quest Speciale" -> "Hai guadagnato $totalPoints punti + quest speciale!"
                    "Doppi Punti" -> "Hai guadagnato $totalPoints punti + doppi punti attivati!"
                    "Premio Settimanale" -> "Hai guadagnato $totalPoints punti + premio speciale!"
                    else -> "Hai guadagnato $totalPoints punti!"
                }
                
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                Log.d("DailyReward", "Claimed reward: ${reward.rewardType} for ${reward.dayName} - $totalPoints points")
                
                // Update progress after claiming
                updateProgress()
            }
        }
    }
    
    private fun updateProgress() {
        val claimedCount = rewards.count { it.isClaimed }
        val totalPoints = claimedCount * 1000 // 1000 punti per ogni ricompensa riscattata
        
        // Update progress bar (max 7000 punti)
        val progressBar = findViewById<ProgressBar>(R.id.progressWeekly)
        progressBar.max = 7000
        progressBar.progress = totalPoints
        
        // Update progress text
        findViewById<TextView>(R.id.tvProgressText).text = "$totalPoints/7000 punti ($claimedCount/7 giorni)"
        
        // Enable special reward if all 7 days completed
        val specialButton = findViewById<Button>(R.id.btnSpecialReward)
        if (claimedCount >= 7) {
            specialButton.isEnabled = true
            specialButton.text = "RISCATTA PREMIO SPECIALE 🎁"
        } else {
            specialButton.isEnabled = false
            specialButton.text = "COMPLETA TUTTI I GIORNI (${7000-totalPoints} punti rimanenti)"
        }
    }
}