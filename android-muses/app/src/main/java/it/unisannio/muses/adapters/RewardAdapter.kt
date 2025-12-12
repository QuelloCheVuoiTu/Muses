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
import it.unisannio.muses.data.models.UserReward
import it.unisannio.muses.data.models.RewardDetails

class RewardAdapter(
    private val rewards: List<RewardDetails>,
    private val onRewardClick: (RewardDetails) -> Unit
) : RecyclerView.Adapter<RewardAdapter.RewardViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RewardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reward, parent, false)
        return RewardViewHolder(view)
    }

    override fun onBindViewHolder(holder: RewardViewHolder, position: Int) {
        holder.bind(rewards[position])
    }

    override fun getItemCount() = rewards.size

    inner class RewardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.cardReward)
        private val ivRewardIcon: ImageView = itemView.findViewById(R.id.ivRewardIcon)
        private val tvRewardTitle: TextView = itemView.findViewById(R.id.tvRewardTitle)
        private val tvRewardDescription: TextView = itemView.findViewById(R.id.tvRewardDescription)
        private val tvRewardAmount: TextView = itemView.findViewById(R.id.tvRewardAmount)
        private val tvRewardStatus: TextView = itemView.findViewById(R.id.tvRewardStatus)

        fun bind(reward: RewardDetails) {
            tvRewardTitle.text = reward.displayTitle
            tvRewardDescription.text = reward.displayDescription
            tvRewardAmount.text = reward.displayAmount
            tvRewardStatus.text = reward.statusText
            
            // Nascondi amount se vuoto
            if (reward.displayAmount.isEmpty()) {
                tvRewardAmount.visibility = View.GONE
            } else {
                tvRewardAmount.visibility = View.VISIBLE
            }
            
            // Set status colors and background
            val context = itemView.context
            if (reward.used) {
                tvRewardStatus.setTextColor(ContextCompat.getColor(context, R.color.error_color))
                tvRewardStatus.setBackgroundResource(R.drawable.status_background_error)
                ivRewardIcon.alpha = 0.5f
                cardView.alpha = 0.7f
            } else {
                tvRewardStatus.setTextColor(ContextCompat.getColor(context, R.color.success_color))
                tvRewardStatus.setBackgroundResource(R.drawable.status_background_success)
                ivRewardIcon.alpha = 1.0f
                cardView.alpha = 1.0f
            }
            
            // Set click listener solo per reward disponibili
            if (reward.isAvailable) {
                cardView.setOnClickListener {
                    onRewardClick(reward)
                }
                cardView.isClickable = true
                cardView.isFocusable = true
            } else {
                cardView.setOnClickListener(null)
                cardView.isClickable = false
                cardView.isFocusable = false
            }
            
            // Set icon based on reward status
            val iconRes = if (reward.used) R.drawable.ic_check else R.drawable.ic_gift
            ivRewardIcon.setImageResource(iconRes)
        }
    }
}