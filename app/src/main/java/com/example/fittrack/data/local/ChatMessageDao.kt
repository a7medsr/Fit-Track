package com.example.fittrack.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE userId = :userId ORDER BY timestamp ASC, id ASC")
    fun observeForUser(userId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE userId = :userId ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun recentForUser(userId: String, limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getById(id: Long): ChatMessageEntity?

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET actionState = :state WHERE id = :id")
    suspend fun setActionState(id: Long, state: String)

    @Query("DELETE FROM chat_messages WHERE userId = :userId")
    suspend fun clearForUser(userId: String)

    /** Moves signed-out history onto an account after sign-in. */
    @Query("UPDATE chat_messages SET userId = :newUserId WHERE userId = :oldUserId")
    suspend fun reassignUser(oldUserId: String, newUserId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheAnswer(entry: AiResponseCacheEntity)

    @Query("SELECT * FROM ai_response_cache WHERE questionHash = :hash AND createdAt > :notBefore")
    suspend fun cachedAnswer(hash: String, notBefore: Long): AiResponseCacheEntity?
}
