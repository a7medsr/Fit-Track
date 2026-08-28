package com.example.fittrack.domain.model

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val id: Long = 0,
    val role: ChatRole,
    val text: String,
    val timestamp: Long,
    /** Set when the assistant proposed a write that is waiting on confirmation. */
    val pendingAction: AiAction? = null,
    val actionState: ActionState = ActionState.NONE
)
