package com.example.fittrack.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One line of chat history.
 *
 * [userId] is the Firebase uid, or "local" while signed out. Every query filters
 * on it, so one account never sees another's conversation on a shared device.
 *
 * Chat history is deliberately NOT mirrored to Firestore: it grows without
 * bound and the sync layer packs each collection into a single document with a
 * 1 MiB ceiling.
 */
@Entity(
    tableName = "chat_messages",
    indices = [Index("userId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val userId: String,
    /** USER or ASSISTANT. */
    val role: String,
    val text: String,
    val timestamp: Long,
    /** Serialised AiAction awaiting confirmation, null when there is none. */
    val pendingActionJson: String? = null,
    val actionState: String = "NONE"
)

/** Cached Tier 3 answers, keyed by a hash of the normalised question. */
@Entity(tableName = "ai_response_cache")
data class AiResponseCacheEntity(
    @PrimaryKey
    val questionHash: String,
    val answer: String,
    val createdAt: Long
)
