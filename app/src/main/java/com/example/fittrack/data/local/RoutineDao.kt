package com.example.fittrack.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    /**
     * Recently logged sessions first. SQLite sorts NULL below any value, so
     * DESC leaves never-used sessions at the bottom.
     */
    @Transaction
    @Query("SELECT * FROM routines ORDER BY lastUsedAt DESC, name ASC")
    fun getAllRoutines(): Flow<List<RoutineWithItems>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutine(id: Long): RoutineWithItems?

    @Transaction
    @Query("SELECT * FROM routines")
    suspend fun getAllOnce(): List<RoutineWithItems>

    @Query("SELECT * FROM routines WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): RoutineEntity?

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Insert
    suspend fun insertItems(items: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun deleteItemsFor(routineId: Long)

    @Query("UPDATE routines SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun markUsed(id: Long, timestamp: Long)

    /**
     * Editing a session rewrites its exercise list wholesale. Doing it in one
     * transaction keeps the session from being observed half-empty.
     */
    @Transaction
    suspend fun replaceItems(routineId: Long, items: List<RoutineExerciseEntity>) {
        deleteItemsFor(routineId)
        insertItems(items)
    }
}
