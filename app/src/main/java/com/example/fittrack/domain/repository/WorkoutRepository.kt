package com.example.fittrack.domain.repository

import com.example.fittrack.domain.model.Workout
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    fun getAllWorkouts(): Flow<List<Workout>>
    suspend fun addWorkout(workout: Workout)
    suspend fun addWorkouts(workouts: List<Workout>)
    suspend fun updateWorkout(workout: Workout)
    suspend fun deleteWorkout(workout: Workout)
    suspend fun seedMockWorkoutsIfNeeded()
}