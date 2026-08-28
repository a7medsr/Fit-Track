package com.example.fittrack.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Insert
    suspend fun insert(workout: WorkoutEntity)

    /** Logging a saved session writes every exercise in one transaction. */
    @Insert
    suspend fun insertAll(workouts: List<WorkoutEntity>)

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Delete
    suspend fun delete(workout: WorkoutEntity)

    @Query("SELECT * FROM workouts ORDER BY date DESC, id DESC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun getCount(): Int

    @Query("SELECT * FROM workouts")
    suspend fun getAllOnce(): List<WorkoutEntity>

    @Query("SELECT syncId FROM workouts")
    suspend fun getAllSyncIds(): List<String>
}