package com.example.museo_gui

import Message
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.museo_gui.databinding.ItemMessageBinding
import java.text.SimpleDateFormat
import java.util.*

class MessagesAdapter(private val messages: MutableList<Message>) :
    RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    class MessageViewHolder(private val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message, dateFormat: SimpleDateFormat) {
            binding.textViewMessage.text = message.text
            binding.textViewTime.text = dateFormat.format(Date(message.timestamp))

            if (message.isUser) {
                binding.messageContainer.background = ContextCompat.getDrawable(
                    binding.root.context,
                    R.drawable.message_background_user
                )
                binding.textViewMessage.setTextColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.white)
                )
                binding.textViewTime.setTextColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.white)
                )
                binding.messageContainer.layoutParams =
                    (binding.messageContainer.layoutParams as LinearLayout.LayoutParams).apply {
                        gravity = android.view.Gravity.END
                    }
            } else {
                binding.messageContainer.background = ContextCompat.getDrawable(
                    binding.root.context,
                    R.drawable.message_background_bot
                )
                binding.textViewMessage.setTextColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.black)
                )
                binding.textViewTime.setTextColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.black)
                )
                binding.messageContainer.layoutParams =
                    (binding.messageContainer.layoutParams as LinearLayout.LayoutParams).apply {
                        gravity = android.view.Gravity.START
                    }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position], dateFormat)
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
}