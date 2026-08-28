package com.example.fittrack.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fittrack.R
import com.example.fittrack.domain.model.ActionState
import com.example.fittrack.domain.model.ChatMessage
import com.example.fittrack.domain.model.ChatRole

class ChatAdapter(
    private val onConfirm: (ChatMessage) -> Unit,
    private val onCancel: (ChatMessage) -> Unit
) : ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(DIFF) {

    /** Set from the ViewModel state before each submitList. */
    var confirmPrompts: Map<Long, String> = emptyMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder =
        MessageViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message, parent, false)
        )

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bubble: TextView = view.findViewById(R.id.bubble)
        private val confirmRow: View = view.findViewById(R.id.confirmRow)
        private val confirmPrompt: TextView = view.findViewById(R.id.confirmPrompt)
        private val confirmBtn: View = view.findViewById(R.id.confirmBtn)
        private val cancelBtn: View = view.findViewById(R.id.cancelBtn)
        private val outcome: TextView = view.findViewById(R.id.actionOutcome)

        fun bind(message: ChatMessage) {
            val context = itemView.context
            val fromUser = message.role == ChatRole.USER

            bubble.text = message.text
            bubble.setBackgroundResource(
                if (fromUser) R.drawable.bg_bubble_user else R.drawable.bg_bubble_assistant
            )
            bubble.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (fromUser) R.color.on_brand else R.color.text_primary
                )
            )
            // Align the row's children rather than the row itself, so a short
            // user message still hugs the right edge.
            (itemView as LinearLayout).gravity = if (fromUser) Gravity.END else Gravity.START

            val pending = message.pendingAction
            val awaiting = pending != null && message.actionState == ActionState.PENDING

            confirmRow.visibility = if (awaiting) View.VISIBLE else View.GONE
            if (awaiting) {
                confirmPrompt.text = confirmPrompts[message.id].orEmpty()
                confirmBtn.setOnClickListener { onConfirm(message) }
                cancelBtn.setOnClickListener { onCancel(message) }
            }

            val outcomeText = when (message.actionState) {
                ActionState.DONE -> context.getString(R.string.chat_action_done)
                ActionState.CANCELLED -> context.getString(R.string.chat_action_cancelled)
                ActionState.FAILED -> context.getString(R.string.chat_action_failed)
                else -> null
            }
            outcome.text = outcomeText.orEmpty()
            outcome.visibility = if (outcomeText == null) View.GONE else View.VISIBLE
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) =
                oldItem == newItem
        }
    }
}
