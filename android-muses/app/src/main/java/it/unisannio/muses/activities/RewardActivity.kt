package it.unisannio.muses.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.unisannio.muses.R
import it.unisannio.muses.adapters.RewardAdapter
import it.unisannio.muses.data.models.UserReward
import it.unisannio.muses.data.models.RewardResponse
import it.unisannio.muses.data.models.RewardDetails
import it.unisannio.muses.data.models.RewardDetailsResponse
import it.unisannio.muses.api.ApiService
import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.helpers.AuthTokenManager
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class RewardActivity : ComponentActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RewardAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var authTokenManager: AuthTokenManager
    private val rewards = mutableListOf<RewardDetails>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reward)

        initViews()
        setupRecyclerView()
        loadUserRewards()
    }
    
    private fun initViews() {
        // Back button
        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            finish()
        }
        
        recyclerView = findViewById(R.id.rvRewards)
        progressBar = findViewById(R.id.progressBar)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        authTokenManager = AuthTokenManager(this)
        
        // Configure RetrofitInstance to use the auth token
        RetrofitInstance.tokenProvider = { authTokenManager.getToken() }
    }

    private fun setupRecyclerView() {
        adapter = RewardAdapter(rewards) { reward ->
            onRewardClicked(reward)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun loadUserRewards() {
        showLoading(true)

        val userId = authTokenManager.getEntityId()
        val token = authTokenManager.getToken()
        Log.d("RewardActivity", "UserId: $userId, Token: $token")
        if (userId == null) {
            showError("User ID not found")
            return
        }
        if (token.isNullOrEmpty()) {
            showError("Token non trovato! Effettua il login.")
            Log.e("RewardActivity", "Token mancante! 401 probabile.")
            showLoading(false)
            return
        }

        Log.d("RewardActivity", "Loading rewards for user: $userId")

        lifecycleScope.launch {
            try {
                val apiService = RetrofitInstance.api
                val response = apiService.getUserRewards(userId)

                showLoading(false)

                if (response.isSuccessful) {
                    val rewardResponse = response.body()
                    if (rewardResponse != null) {
                        Log.d("RewardActivity", "Loaded ${rewardResponse.count} rewards")
                        loadRewardDetails(rewardResponse.rewards)
                    } else {
                        showError("No rewards data received")
                    }
                } else {
                    Log.e("RewardActivity", "Error loading rewards: ${response.code()} - ${response.message()}")
                    showError("Error loading rewards: ${response.code()}")
                }
            } catch (t: Throwable) {
                showLoading(false)
                Log.e("RewardActivity", "Network error loading rewards", t)
                showError("Network error: ${t.message}")
            }
        }
    }
    
    private suspend fun loadRewardDetails(userRewards: List<UserReward>) {
        val detailedRewards = mutableListOf<RewardDetails>()
        val apiService = RetrofitInstance.api
        
        for (userReward in userRewards) {
            try {
                Log.d("RewardActivity", "Loading details for reward: ${userReward.id}")
                val detailResponse = apiService.getRewardDetails(userReward.id)
                
                if (detailResponse.isSuccessful) {
                    val rewardDetailsResponse = detailResponse.body()
                    if (rewardDetailsResponse != null) {
                        val rewardInfo = rewardDetailsResponse.reward
                        val detailedReward = RewardDetails(
                            id = userReward.id,
                            rewardId = userReward.rewardId,
                            used = userReward.used,
                            userId = userReward.userId,
                            amount = rewardInfo.amount,
                            description = rewardInfo.description,
                            museumId = rewardInfo.museumId,
                            reductionType = rewardInfo.reductionType,
                            subject = rewardInfo.subject
                        )
                        detailedRewards.add(detailedReward)
                        Log.d("RewardActivity", "Loaded details for: ${rewardInfo.subject}")
                    }
                } else {
                    Log.e("RewardActivity", "Failed to load details for reward ${userReward.id}: ${detailResponse.code()}")
                    // Aggiungo comunque la reward senza dettagli
                    val basicReward = RewardDetails(
                        id = userReward.id,
                        rewardId = userReward.rewardId,
                        used = userReward.used,
                        userId = userReward.userId
                    )
                    detailedRewards.add(basicReward)
                }
            } catch (e: Exception) {
                Log.e("RewardActivity", "Error loading details for reward ${userReward.id}", e)
                // Aggiungo comunque la reward senza dettagli
                val basicReward = RewardDetails(
                    id = userReward.id,
                    rewardId = userReward.rewardId,
                    used = userReward.used,
                    userId = userReward.userId
                )
                detailedRewards.add(basicReward)
            }
        }
        
        // Aggiorna l'UI sul thread principale
        updateRewards(detailedRewards)
        showLoading(false)
    }
    
    private fun updateRewards(newRewards: List<RewardDetails>) {
        rewards.clear()
        rewards.addAll(newRewards)
        adapter.notifyDataSetChanged()
        
        if (rewards.isEmpty()) {
            showEmptyState()
        } else {
            hideEmptyState()
        }
    }
    
    private fun onRewardClicked(reward: RewardDetails) {
        if (reward.used) {
            Toast.makeText(this, "This reward has already been used", Toast.LENGTH_SHORT).show()
        } else {
            // Apri la pagina di dettaglio della reward
            val intent = Intent(this, RewardDetailActivity::class.java)
            intent.putExtra(RewardDetailActivity.EXTRA_REWARD_DETAILS, reward)
            startActivity(intent)
        }
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        showEmptyState()
    }
    
    private fun showEmptyState() {
        emptyStateLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }
    
    private fun hideEmptyState() {
        emptyStateLayout.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
}