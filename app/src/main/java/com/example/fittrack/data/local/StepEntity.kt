package com.example.fittrack.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_steps")
data class StepEntity(
    @PrimaryKey
    val date: String,
    val stepCount: Int,
    val sensorBaseline: Int,
    val lastUpdated: Long
)