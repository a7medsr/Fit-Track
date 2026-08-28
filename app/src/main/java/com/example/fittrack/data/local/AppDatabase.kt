package com.example.fittrack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        StepEntity::class,
        WorkoutEntity::class,
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stepDao(): StepDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
}
