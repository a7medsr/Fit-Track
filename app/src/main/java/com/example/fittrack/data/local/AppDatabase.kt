package com.example.fittrack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [StepEntity::class, WorkoutEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stepDao(): StepDao
    abstract fun workoutDao(): WorkoutDao
}