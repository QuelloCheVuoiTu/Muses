package it.unisannio.muses.activities

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import it.unisannio.muses.R
import it.unisannio.muses.data.models.RewardDetails
import it.unisannio.muses.data.models.Museum
import it.unisannio.muses.api.RetrofitInstance
import it.unisannio.muses.helpers.AuthTokenManager
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.graphics.Bitmap
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.BarcodeEncoder

class RewardDetailActivity : ComponentActivity() {
    
    private lateinit var tvRewardTitle: TextView
    private lateinit var tvRewardDescription: TextView
    private lateinit var tvRewardAmount: TextView
    private lateinit var tvRewardType: TextView
    private lateinit var tvRewardStatus: TextView
    private lateinit var tvMuseumId: TextView
    private lateinit var ivRewardIcon: ImageView
    private lateinit var btnUseReward: MaterialButton
    private lateinit var authTokenManager: AuthTokenManager
    private lateinit var layoutQRSection: LinearLayout
    private lateinit var ivQRCode: ImageView
    private var isQRSectionExpanded = false

    companion object {
        const val EXTRA_REWARD_DETAILS = "extra_reward_details"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reward_detail)

        initViews()
        loadRewardData()
    }
    
    private fun initViews() {
        // Back button
        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            finish()
        }
        
        tvRewardTitle = findViewById(R.id.tvRewardTitle)
        tvRewardDescription = findViewById(R.id.tvRewardDescription)
        tvRewardAmount = findViewById(R.id.tvRewardAmount)
        tvRewardType = findViewById(R.id.tvRewardType)
        tvRewardStatus = findViewById(R.id.tvRewardStatus)
        tvMuseumId = findViewById(R.id.tvMuseumId)
        ivRewardIcon = findViewById(R.id.ivRewardIcon)
        btnUseReward = findViewById<MaterialButton>(R.id.btnUseReward)
        layoutQRSection = findViewById(R.id.layoutQRSection)
        ivQRCode = findViewById(R.id.ivQRCode)
        authTokenManager = AuthTokenManager(this)
        
        // Configure RetrofitInstance to use the auth token
        RetrofitInstance.tokenProvider = { authTokenManager.getToken() }
        
        btnUseReward.setOnClickListener {
            toggleQRSection()
        }
    }

    private fun loadRewardData() {
        val rewardDetails = intent.getSerializableExtra(EXTRA_REWARD_DETAILS) as? RewardDetails
        
        if (rewardDetails == null) {
            Log.e("RewardDetailActivity", "No reward details provided")
            Toast.makeText(this, "Errore nel caricamento dei dettagli", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Popola i campi con i dettagli della reward
        tvRewardTitle.text = rewardDetails.displayTitle
        tvRewardDescription.text = rewardDetails.displayDescription
        tvRewardAmount.text = rewardDetails.displayAmount
        tvRewardType.text = rewardDetails.reductionType ?: "N/A"
        tvRewardStatus.text = rewardDetails.statusText
        
        // Carica il nome del museo se disponibile
        if (rewardDetails.museumId != null) {
            loadMuseumName(rewardDetails.museumId)
        } else {
            tvMuseumId.text = "Museum: N/A"
        }
        
        // Configura il pulsante in base allo stato
        if (rewardDetails.isAvailable) {
            btnUseReward.isEnabled = true
            btnUseReward.text = "Use Reward"
            tvRewardStatus.setTextColor(getColor(R.color.success_color))
        } else {
            btnUseReward.isEnabled = false
            btnUseReward.text = "Already Used"
            tvRewardStatus.setTextColor(getColor(R.color.error_color))
        }
        
        // Configura l'icona
        if (rewardDetails.used) {
            ivRewardIcon.setImageResource(R.drawable.ic_check)
            ivRewardIcon.alpha = 0.5f
        } else {
            ivRewardIcon.setImageResource(R.drawable.ic_gift)
            ivRewardIcon.alpha = 1.0f
        }
        
        Log.d("RewardDetailActivity", "Loaded reward: ${rewardDetails.displayTitle}")
    }
    
    private fun loadMuseumName(museumId: String) {
        lifecycleScope.launch {
            try {
                Log.d("RewardDetailActivity", "Loading museum name for ID: $museumId")
                val apiService = RetrofitInstance.api
                val response = apiService.getMuseumById(museumId)
                
                if (response.isSuccessful) {
                    val museum = response.body()
                    if (museum != null) {
                        tvMuseumId.text = "Museum: ${museum.name}"
                        Log.d("RewardDetailActivity", "Loaded museum name: ${museum.name}")
                    } else {
                        tvMuseumId.text = "Museum: Unknown"
                        Log.w("RewardDetailActivity", "Museum data is null")
                    }
                } else {
                    tvMuseumId.text = "Museum: Unknown"
                    Log.e("RewardDetailActivity", "Failed to load museum: ${response.code()}")
                }
            } catch (e: Exception) {
                tvMuseumId.text = "Museum: Unknown"
                Log.e("RewardDetailActivity", "Error loading museum name", e)
            }
        }
    }
    
    private fun toggleQRSection() {
        if (isQRSectionExpanded) {
            // Collapse QR section
            layoutQRSection.visibility = View.GONE
            btnUseReward.text = "Use Reward"
            btnUseReward.setIconResource(R.drawable.ic_gift)
            isQRSectionExpanded = false
            Log.d("RewardDetailActivity", "QR section collapsed")
        } else {
            // Expand QR section and generate QR code
            val rewardDetails = intent.getSerializableExtra(EXTRA_REWARD_DETAILS) as? RewardDetails
            if (rewardDetails != null) {
                generateQRCode(rewardDetails.id)
                layoutQRSection.visibility = View.VISIBLE
                btnUseReward.text = "Hide QR Code"
                btnUseReward.setIconResource(R.drawable.ic_arrow_back)
                isQRSectionExpanded = true
                Log.d("RewardDetailActivity", "QR section expanded")
            } else {
                Toast.makeText(this, "Error: Reward details not available", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun generateQRCode(rewardId: String) {
        try {
            Log.d("RewardDetailActivity", "Generating QR code for reward ID: $rewardId")
            
            val multiFormatWriter = MultiFormatWriter()
            val bitMatrix: BitMatrix = multiFormatWriter.encode(rewardId, BarcodeFormat.QR_CODE, 500, 500)
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.createBitmap(bitMatrix)
            
            ivQRCode.setImageBitmap(bitmap)
            
            Log.d("RewardDetailActivity", "QR code generated successfully")
        } catch (e: Exception) {
            Log.e("RewardDetailActivity", "Error generating QR code", e)
            Toast.makeText(this, "Error generating QR code", Toast.LENGTH_SHORT).show()
        }
    }
}