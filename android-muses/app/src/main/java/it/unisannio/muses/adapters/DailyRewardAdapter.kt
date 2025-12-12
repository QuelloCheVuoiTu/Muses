package it.unisannio.muses.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import it.unisannio.muses.R
import it.unisannio.muses.data.models.DailyReward

class DailyRewardAdapter(
    private val rewards: List<DailyReward>,
    private val onRewardClick: (DailyReward) -> Unit
) : RecyclerView.Adapter<DailyRewardAdapter.RewardViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RewardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_daily_reward, parent, false)
        return RewardViewHolder(view)
    }

    override fun onBindViewHolder(holder: RewardViewHolder, position: Int) {
        holder.bind(rewards[position])
    }

    override fun getItemCount() = rewards.size

    inner class RewardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.cardDailyReward)
        private val tvDayName: TextView = itemView.findViewById(R.id.tvDayName)
        private val tvDayNumber: TextView = itemView.findViewById(R.id.tvDayNumber)
        private val ivRewardIcon: ImageView = itemView.findViewById(R.id.ivRewardIcon)
        private val tvRewardType: TextView = itemView.findViewById(R.id.tvRewardType)
        private val tvRewardValue: TextView = itemView.findViewById(R.id.tvRewardValue)
        private val tvBasePoints: TextView = itemView.findViewById(R.id.tvBasePoints)
        private val ivStatusIcon: ImageView = itemView.findViewById(R.id.ivStatusIcon)

        fun bind(reward: DailyReward) {
            tvDayName.text = reward.dayName
            tvDayNumber.text = reward.dayNumber.toString()
            tvRewardType.text = reward.rewardDescription // Usa la descrizione invece del tipo
            ivRewardIcon.setImageResource(reward.iconResource)

            // Format reward value based on type
            val valueText = when (reward.rewardType) {
                "discount" -> "${reward.rewardValue}% OFF"
                "points" -> "+${reward.rewardValue} extra"
                "gadget" -> "Sbloccabile"
                "bonus" -> "Funzionalità Extra"
                "quest" -> "Missione Speciale"
                "special" -> "Esclusivo"
                else -> reward.rewardDescription
            }
            tvRewardValue.text = valueText
            
            // Always show base points (1000 punti per ogni ricompensa)
            tvBasePoints.text = "${reward.basePoints} punti"

            // Set card appearance based on status
            when {
                reward.isClaimed -> {
                    // Claimed - Green with check mark
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.reward_claimed))
                    ivStatusIcon.setImageResource(R.drawable.ic_check)
                    ivStatusIcon.visibility = View.VISIBLE
                    cardView.alpha = 0.8f
                }
                reward.isToday && !reward.isClaimed -> {
                    // Today and not claimed - Highlighted and clickable
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.reward_today))
                    ivStatusIcon.visibility = View.GONE
                    cardView.alpha = 1.0f
                }
                !reward.isToday && !reward.isClaimed -> {
                    // Not today and not claimed - Gray and disabled
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.reward_unavailable))
                    ivStatusIcon.setImageResource(R.drawable.ic_lock)
                    ivStatusIcon.visibility = View.VISIBLE
                    cardView.alpha = 0.5f
                }
                else -> {
                    // Fallback case
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.reward_available))
                    ivStatusIcon.visibility = View.GONE
                    cardView.alpha = 1.0f
                }
            }

            // Set click listener
            cardView.setOnClickListener {
                onRewardClick(reward)
            }
        }
    }
}