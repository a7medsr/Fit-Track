package com.example.fittrack.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One row of the exercise catalogue: both the exercises shipped in
 * `assets/exercises.json` and the ones the user creates by hand.
 *
 * [catalogKey] is the stable identifier of a bundled exercise and is what the
 * seeder matches on, so re-seeding after an app update adds newly shipped
 * exercises without touching the user's favourites. Custom rows leave it null;
 * SQLite allows any number of NULLs in a unique index.
 */
@Entity(
    tableName = "exercises",
    indices = [Index(value = ["catalogKey"], unique = true)]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val catalogKey: String? = null,
    val name: String,
    val category: String,
    /** Metabolic equivalent, the input to the kcal formula. */
    val met: Double,
    val icon: String,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    /** LIGHT / MODERATE / INTENSE for custom exercises, null for bundled ones. */
    val intensity: String? = null,
    /** Epoch millis of the last time this exercise was logged, null if never. */
    val lastUsedAt: Long? = null,

    /** Stable identity across devices, for the cloud mirror. */
    @ColumnInfo(defaultValue = "")
    val syncId: String = UUID.randomUUID().toString()
)
