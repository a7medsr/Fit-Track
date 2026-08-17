package com.example.fittrack.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(step: StepEntity)

    @Query("SELECT * FROM daily_steps WHERE date = :date")
    fun getStepsForDate(date: String): Flow<StepEntity?>

    @Query("SELECT * FROM daily_steps WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getStepsInRange(startDate: String, endDate: String): Flow<List<StepEntity>>
}