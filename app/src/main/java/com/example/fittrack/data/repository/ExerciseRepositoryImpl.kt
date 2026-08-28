package com.example.fittrack.data.repository

import com.example.fittrack.data.local.ExerciseDao
import com.example.fittrack.data.local.ExerciseEntity
import com.example.fittrack.data.seed.ExerciseSeeder
import com.example.fittrack.domain.model.Exercise
import com.example.fittrack.domain.model.ExerciseCategory
import com.example.fittrack.domain.model.ExerciseIntensity
import com.example.fittrack.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ExerciseRepositoryImpl @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val exerciseSeeder: ExerciseSeeder
) : ExerciseRepository {

    override fun getAllExercises(): Flow<List<Exercise>> =
        exerciseDao.getAllExercises().map { list -> list.map { it.toDomain() } }

    override suspend fun getExercise(id: Long): Exercise? = exerciseDao.getById(id)?.toDomain()

    override suspend fun seedCatalogIfNeeded() {
        val alreadySeeded = exerciseDao.getCatalogKeys().toSet()
        val missing = exerciseSeeder.loadCatalog().filter { it.catalogKey !in alreadySeeded }
        if (missing.isNotEmpty()) exerciseDao.insertAll(missing)
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) {
        exerciseDao.setFavorite(id, favorite)
    }

    override suspend fun markUsed(id: Long) {
        exerciseDao.markUsed(id, System.currentTimeMillis())
    }

    override suspend fun createCustomExercise(
        name: String,
        category: ExerciseCategory,
        icon: String,
        intensity: ExerciseIntensity
    ): Long = exerciseDao.insert(
        ExerciseEntity(
            catalogKey = null,
            name = name,
            category = category.storageName,
            met = intensity.met,
            icon = icon,
            isCustom = true,
            intensity = intensity.name
        )
    )

    override suspend fun updateCustomExercise(
        id: Long,
        name: String,
        category: ExerciseCategory,
        icon: String,
        intensity: ExerciseIntensity
    ) {
        val existing = exerciseDao.getById(id) ?: return
        // Only custom rows are editable; a bundled MET value must stay as measured.
        if (!existing.isCustom) return
        exerciseDao.update(
            existing.copy(
                name = name,
                category = category.storageName,
                met = intensity.met,
                icon = icon,
                intensity = intensity.name
            )
        )
    }

    override suspend fun deleteCustomExercise(id: Long) {
        val existing = exerciseDao.getById(id) ?: return
        if (!existing.isCustom) return
        exerciseDao.delete(existing)
    }

    private fun ExerciseEntity.toDomain(): Exercise = Exercise(
        id = id,
        name = name,
        // A hand-edited assets file could carry an unknown category; fall back
        // rather than throw while mapping a whole list.
        category = ExerciseCategory.fromStorage(category) ?: ExerciseCategory.CARDIO,
        met = met,
        icon = icon,
        isCustom = isCustom,
        isFavorite = isFavorite,
        intensity = ExerciseIntensity.fromStorage(intensity),
        lastUsedAt = lastUsedAt
    )
}
