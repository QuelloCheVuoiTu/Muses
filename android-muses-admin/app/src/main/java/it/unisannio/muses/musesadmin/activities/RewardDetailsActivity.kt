package it.unisannio.muses.musesadmin.activities

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unisannio.muses.musesadmin.R
import it.unisannio.muses.musesadmin.api.RetrofitInstance
import it.unisannio.muses.musesadmin.data.models.Reward
import it.unisannio.muses.musesadmin.data.repositories.RewardRepository
import kotlinx.coroutines.launch

class RewardDetailsActivity : AppCompatActivity() {
    private lateinit var textSubject: TextView
    private lateinit var textDescription: TextView
    private lateinit var textAmount: TextView
    private lateinit var textReductionType: TextView
    private lateinit var btnValidate: Button
    private lateinit var progressBar: ProgressBar
    
    private lateinit var rewardId: String
    private lateinit var reward: Reward

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reward_details)

        // Initialize views
        textSubject = findViewById(R.id.textSubject)
        textDescription = findViewById(R.id.textDescription)
        textAmount = findViewById(R.id.textAmount)
        textReductionType = findViewById(R.id.textReductionType)
        btnValidate = findViewById(R.id.btnValidate)
        progressBar = findViewById(R.id.progressBar)

        // Get data from intent
        rewardId = intent.getStringExtra("REWARD_ID") ?: ""
        reward = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("REWARD", Reward::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("REWARD") as? Reward
        } ?: run {
            Toast.makeText(this, "Error: Reward data not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Display reward details
        displayRewardDetails()

        // Set up validate button
        btnValidate.setOnClickListener {
            validateReward()
        }
    }

    private fun displayRewardDetails() {
        textSubject.text = reward.subject
        textDescription.text = reward.description
        textAmount.text = "${reward.amount}%"
        textReductionType.text = reward.reduction_type
    }

    private fun validateReward() {
        btnValidate.isEnabled = false
        progressBar.visibility = View.VISIBLE

        val viewModel = RewardDetailsViewModel(RewardRepository(RetrofitInstance.api))
        viewModel.useReward(
            rewardId = rewardId,
            onSuccess = { code ->
                progressBar.visibility = View.GONE
                btnValidate.isEnabled = true
                
                val message = when (code) {
                    200 -> {
                        btnValidate.text = "VALIDATED ✓"
                        btnValidate.isEnabled = false
                        "Reward validated successfully!"
                    }
                    400 -> "Error: Reward has already been used"
                    417 -> "Error: Reward has expired"
                    else -> "Error: Unexpected response code $code"
                }
                
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            },
            onError = { error ->
                progressBar.visibility = View.GONE
                btnValidate.isEnabled = true
                Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
            }
        )
    }
}

class RewardDetailsViewModel(private val rewardRepository: RewardRepository) : ViewModel() {
    
    fun useReward(
        rewardId: String,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = rewardRepository.useReward(rewardId)
            result.onSuccess { useRewardResponse ->
                onSuccess(useRewardResponse.code)
            }.onFailure { error ->
                onError(error.message ?: "Unknown error occurred")
            }
        }
    }
}