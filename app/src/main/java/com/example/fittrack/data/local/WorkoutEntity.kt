package com.example.fittrack.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val type: String,
    val durationMinutes: Int,
    val calories: Int,
    val date: String,
    val notes: String? = null,
    /**
     * Emoji of the exercise this was logged from. Null on rows written before
     * the exercise library existed, where History falls back to its own mapping.
     */
    val exerciseIcon: String? = null,
    /** Name of the saved session this was logged from, null for a one-off. */
    val sessionName: String? = null,

    /**
     * Stable identity across devices. Room's autoincrement id is local only, so
     * the cloud copy is keyed on this instead.
     */
    @ColumnInfo(defaultValue = "")
    val syncId: String = UUID.randomUUID().toString()
)
