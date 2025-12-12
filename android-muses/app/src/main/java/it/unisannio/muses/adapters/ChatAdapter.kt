package it.unisannio.muses.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import it.unisannio.muses.R
import it.unisannio.muses.data.models.ChatMessage

class ChatAdapter(private val messages: MutableList<ChatMessage>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_BOT = 2
        private const val VIEW_TYPE_TYPING = 3
    }

    private var showTypingIndicator = false

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userMessageContainer: LinearLayout = itemView.findViewById(R.id.userMessageContainer)
        val userMessageText: TextView = itemView.findViewById(R.id.userMessageText)
        val botMessageContainer: LinearLayout = itemView.findViewById(R.id.botMessageContainer)
        val botMessageText: TextView = itemView.findViewById(R.id.botMessageText)
    }

    class TypingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun getItemViewType(position: Int): Int {
        return if (showTypingIndicator && position == messages.size) {
            VIEW_TYPE_TYPING
        } else {
            if (messages[position].isFromUser) VIEW_TYPE_USER else VIEW_TYPE_BOT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_TYPING -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_typing_indicator, parent, false)
                TypingViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_message, parent, false)
                ChatViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ChatViewHolder -> {
                val message = messages[position]
                if (message.isFromUser) {
                    // Messaggio dell'utente
                    holder.userMessageContainer.visibility = View.VISIBLE
                    holder.userMessageText.text = message.text
                    holder.botMessageContainer.visibility = View.GONE
                } else {
                    // Messaggio del bot con formattazione migliorata
                    holder.botMessageContainer.visibility = View.VISIBLE
                    holder.botMessageText.text = formatBotMessage(message.text)
                    holder.userMessageContainer.visibility = View.GONE
                }
            }
            is TypingViewHolder -> {
                // Typing indicator - niente da bindare, è già configurato nel layout
            }
        }
    }

    override fun getItemCount(): Int = messages.size + if (showTypingIndicator) 1 else 0

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun showTypingIndicator() {
        if (!showTypingIndicator) {
            showTypingIndicator = true
            notifyItemInserted(messages.size)
        }
    }

    fun hideTypingIndicator() {
        if (showTypingIndicator) {
            showTypingIndicator = false
            notifyItemRemoved(messages.size)
        }
    }

    private fun formatBotMessage(text: String): String {
        return text
            // Rimuove asterischi per grassetto (**testo** -> testo)
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            // Rimuove asterischi singoli per corsivo (*testo* -> testo)  
            .replace(Regex("\\*(.*?)\\*"), "$1")
            // Rimuove underscore per corsivo (_testo_ -> testo)
            .replace(Regex("_(.*?)_"), "$1")
            // Rimuove hashtag per titoli (## Titolo -> Titolo)
            .replace(Regex("#+\\s*(.+)"), "$1")
            // Migliora le liste puntate
            .replace(Regex("^-\\s+", RegexOption.MULTILINE), "• ")
            .replace(Regex("^\\*\\s+", RegexOption.MULTILINE), "• ")
            .replace(Regex("^\\+\\s+", RegexOption.MULTILINE), "• ")
            // Migliora liste numerate (1. -> 1. )
            .replace(Regex("^(\\d+)\\.\\s*", RegexOption.MULTILINE), "$1. ")
            // Rimuove backticks per codice (`codice` -> codice)
            .replace(Regex("`([^`]*)`"), "$1")
            // Migliora la formattazione dei paragrafi
            .replace(Regex("\\n\\s*\\n"), "\n\n")
            // Pulisce spazi multipli
            .replace(Regex("\\s+"), " ")
            // Migliora la punteggiatura
            .replace(". ", ". ")
            .replace("! ", "! ")
            .replace("? ", "? ")
            // Rimuove spazi all'inizio e fine
            .trim()
    }
}
