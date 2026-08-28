package com.example.fittrack.data.local

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A session joined to its exercise rows, which are themselves joined to the
 * catalogue. Room resolves the nesting, so one query returns everything the
 * Log screen needs to price a session.
 */
data class RoutineWithItems(
    @Embedded val routine: RoutineEntity,
    @Relation(
        entity = RoutineExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "routineId"
    )
    val items: List<RoutineItemWithExercise>
)

data class RoutineItemWithExercise(
    @Embedded val item: RoutineExerciseEntity,
    @Relation(
        parentColumn = "exerciseId",
        entityColumn = "id"
    )
    val exercise: ExerciseEntity?
)
