package it.unisannio.muses.data.models

import com.google.gson.annotations.SerializedName

data class RewardResponse(
    @SerializedName("count")
    val count: Int,
    
    @SerializedName("rewards")
    val rewards: List<UserReward>
)

data class UserReward(
    @SerializedName("_id")
    val id: String,
    
    @SerializedName("reward_id")
    val rewardId: String,
    
    @SerializedName("used")
    val used: Boolean,
    
    @SerializedName("user_id")
    val userId: String
) {
    // Helper property to get display title
    val displayTitle: String
        get() = "Reward #${rewardId.takeLast(8)}"
    
    // Helper property to get status text
    val statusText: String
        get() = if (used) "USED" else "AVAILABLE"
    
    // Helper property to check if reward is available
    val isAvailable: Boolean
        get() = !used
}

data class RewardDetailsResponse(
    @SerializedName("reward")
    val reward: RewardInfo
)

data class RewardInfo(
    @SerializedName("amount")
    val amount: Int,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("museum_id")
    val museumId: String,
    
    @SerializedName("reduction_type")
    val reductionType: String,
    
    @SerializedName("subject")
    val subject: String
)

// Classe combinata che unisce UserReward con i dettagli
data class RewardDetails(
    // Dati base dalla prima chiamata
    val id: String,
    val rewardId: String,
    val used: Boolean,
    val userId: String,
    
    // Dettagli dalla seconda chiamata
    val amount: Int? = null,
    val description: String? = null,
    val museumId: String? = null,
    val reductionType: String? = null,
    val subject: String? = null
) : java.io.Serializable {
    // Helper property to get display title
    val displayTitle: String
        get() = subject ?: "Reward"
    
    // Helper property to get display description
    val displayDescription: String
        get() = description ?: "No description available"
    
    // Helper property to get amount display
    val displayAmount: String
        get() = if (amount != null) {
            when (reductionType) {
                "flat" -> "€${amount} OFF"
                "percentage" -> "${amount}% OFF"
                else -> "$amount"
            }
        } else ""
    
    // Helper property to get status text
    val statusText: String
        get() = if (used) "USED" else "AVAILABLE"
    
    // Helper property to check if reward is available
    val isAvailable: Boolean
        get() = !used
}