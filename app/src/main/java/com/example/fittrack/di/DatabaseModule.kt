package com.example.fittrack.di

import android.content.Context
import androidx.room.Room
import com.example.fittrack.data.local.AppDatabase
import com.example.fittrack.data.local.ChatMessageDao
import com.example.fittrack.data.local.ExerciseDao
import com.example.fittrack.data.local.Migrations
import com.example.fittrack.data.local.RoutineDao
import com.example.fittrack.data.local.StepDao
import com.example.fittrack.data.local.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "fittrack_database"
        )
            // Explicit migrations, not fallbackToDestructiveMigration: an upgrade
            // must never throw away logged workouts or step history.
            .addMigrations(*Migrations.ALL)
            .build()
    }

    @Provides
    fun provideStepDao(database: AppDatabase): StepDao {
        return database.stepDao()
    }
    @Provides
    fun provideWorkoutDao(database: AppDatabase): WorkoutDao {
        return database.workoutDao()
    }
    @Provides
    fun provideExerciseDao(database: AppDatabase): ExerciseDao {
        return database.exerciseDao()
    }
    @Provides
    fun provideRoutineDao(database: AppDatabase): RoutineDao {
        return database.routineDao()
    }
    @Provides
    fun provideChatMessageDao(database: AppDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }
}
