package com.example.fittrack.data.repository

import com.example.fittrack.data.local.ExerciseEntity
import com.example.fittrack.data.local.RoutineDao
import com.example.fittrack.data.local.RoutineEntity
import com.example.fittrack.data.local.RoutineExerciseEntity
import com.example.fittrack.data.local.RoutineWithItems
import com.example.fittrack.domain.model.Exercise
import com.example.fittrack.domain.model.ExerciseCategory
import com.example.fittrack.domain.model.ExerciseIntensity
import com.example.fittrack.domain.model.Routine
import com.example.fittrack.domain.model.RoutineItem
import com.example.fittrack.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoutineRepositoryImpl @Inject constructor(
    private val routineDao: RoutineDao
) : RoutineRepository {

    override fun getAllRoutines(): Flow<List<Routine>> =
        routineDao.getAllRoutines().map { list -> list.map { it.toDomain() } }

    override suspend fun getRoutine(id: Long): Routine? = routineDao.getRoutine(id)?.toDomain()

    override suspend fun createRoutine(
        name: String,
        icon: String,
        items: List<RoutineItem>
    ): Long {
        val routineId = routineDao.insertRoutine(
            RoutineEntity(
                name = name,
                icon = icon,
                createdAt = System.currentTimeMillis()
            )
        )
        routineDao.insertItems(items.toEntities(routineId))
        return routineId
    }

    override suspend fun updateRoutine(
        id: Long,
        name: String,
        icon: String,
        items: List<RoutineItem>
    ) {
        val existing = routineDao.getRoutine(id)?.routine ?: return
        routineDao.updateRoutine(existing.copy(name = name, icon = icon))
        routineDao.replaceItems(id, items.toEntities(id))
    }

    override suspend fun deleteRoutine(id: Long) {
        // routine_exercises rows go with it, via ON DELETE CASCADE.
        routineDao.deleteRoutine(id)
    }

    override suspend fun markUsed(id: Long) {
        routineDao.markUsed(id, System.currentTimeMillis())
    }

    private fun List<RoutineItem>.toEntities(routineId: Long): List<RoutineExerciseEntity> =
        mapIndexed { index, item ->
            RoutineExerciseEntity(
                routineId = routineId,
                exerciseId = item.exercise.id,
                durationMinutes = item.durationMinutes,
                position = index
            )
        }

    private fun RoutineWithItems.toDomain(): Routine = Routine(
        id = routine.id,
        name = routine.name,
        icon = routine.icon,
        items = items
            .sortedBy { it.item.position }
            // exercise is null only if a row outlived its catalogue entry, which
            // the cascade should prevent; drop it rather than show a blank line.
            .mapNotNull { row ->
                row.exercise?.let { RoutineItem(it.toDomain(), row.item.durationMinutes) }
            },
        lastUsedAt = routine.lastUsedAt
    )

    private fun ExerciseEntity.toDomain(): Exercise = Exercise(
        id = id,
        name = name,
        category = ExerciseCategory.fromStorage(category) ?: ExerciseCategory.CARDIO,
        met = met,
        icon = icon,
        isCustom = isCustom,
        isFavorite = isFavorite,
        intensity = ExerciseIntensity.fromStorage(intensity),
        lastUsedAt = lastUsedAt
    )
}
