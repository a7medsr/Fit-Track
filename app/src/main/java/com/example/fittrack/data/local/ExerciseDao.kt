package com.example.fittrack.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntity?

    @Query("SELECT catalogKey FROM exercises WHERE catalogKey IS NOT NULL")
    suspend fun getCatalogKeys(): List<String>

    @Query("SELECT * FROM exercises")
    suspend fun getAllOnce(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE catalogKey = :catalogKey LIMIT 1")
    suspend fun getByCatalogKey(catalogKey: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): ExerciseEntity?

    /**
     * IGNORE rather than REPLACE: the unique index on catalogKey makes a repeat
     * seed a no-op instead of resetting favourites and last-used timestamps.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exercise: ExerciseEntity): Long

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Delete
    suspend fun delete(exercise: ExerciseEntity)

    @Query("UPDATE exercises SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE exercises SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun markUsed(id: Long, timestamp: Long)
}
