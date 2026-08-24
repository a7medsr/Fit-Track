package com.example.fittrack.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val type: String,
    val durationMinutes: Int,
    val calories: Int,
    val date: String,
    val notes: String? = null
)