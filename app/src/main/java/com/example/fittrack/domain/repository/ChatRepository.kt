package com.example.fittrack.domain.repository

import com.example.fittrack.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/** Why the assistant could not answer, so the UI can say something useful. */
sealed class ChatError {
    object NoApiKey : ChatError()
    object Offline : ChatError()
    object RateLimited : ChatError()
    data class Upstream(val message: String) : ChatError()
}

interface ChatRepository {
    /** History for the signed-in user, oldest first. */
    fun observeMessages(): Flow<List<ChatMessage>>

    /** Sends a user message and appends the assistant's reply. */
    suspend fun send(text: String): ChatError?

    /** Runs a write the user confirmed. */
    suspend fun confirmAction(messageId: Long): ChatError?

    suspend fun cancelAction(messageId: Long)

    suspend fun clearHistory()

    /** Re-homes messages written while signed out onto the new account. */
    suspend fun adoptLocalHistory(uid: String)
}
