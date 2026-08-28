package com.example.fittrack.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A saved gym session — "Push Day", "Leg Day" — that the user builds once and
 * then logs in a single tap. The exercises themselves live in
 * [RoutineExerciseEntity], one row per exercise in the session.
 */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val icon: String,
    val createdAt: Long,
    /** Epoch millis this session was last logged, null if never. */
    val lastUsedAt: Long? = null,

    /** Stable identity across devices, for the cloud mirror. */
    @ColumnInfo(defaultValue = "")
    val syncId: String = UUID.randomUUID().toString()
)

/**
 * One exercise inside a session, with the duration the user set for it.
 *
 * Both foreign keys cascade: deleting a session drops its rows, and deleting a
 * custom exercise removes it from every session that used it, so a session can
 * never point at an exercise that no longer exists.
 */
@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId"), Index("exerciseId")]
)
data class RoutineExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val routineId: Long,
    val exerciseId: Long,
    val durationMinutes: Int,
    /** Order within the session, so the list reads back the way it was built. */
    val position: Int
)
