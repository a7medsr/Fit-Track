package com.example.fittrack.domain.repository

import com.example.fittrack.domain.model.Exercise
import com.example.fittrack.domain.model.ExerciseCategory
import com.example.fittrack.domain.model.ExerciseIntensity
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun getAllExercises(): Flow<List<Exercise>>
    suspend fun getExercise(id: Long): Exercise?
    /** Copies anything from the bundled catalogue that is not in the table yet. */
    suspend fun seedCatalogIfNeeded()
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun markUsed(id: Long)
    suspend fun createCustomExercise(
        name: String,
        category: ExerciseCategory,
        icon: String,
        intensity: ExerciseIntensity
    ): Long
    suspend fun updateCustomExercise(
        id: Long,
        name: String,
        category: ExerciseCategory,
        icon: String,
        intensity: ExerciseIntensity
    )
    suspend fun deleteCustomExercise(id: Long)
}
