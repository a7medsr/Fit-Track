package com.example.fittrack.domain.model

/**
 * A saved gym session: a named group of exercises with a duration each, so the
 * whole thing can be logged in one tap instead of exercise by exercise.
 */
data class Routine(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val items: List<RoutineItem> = emptyList(),
    val lastUsedAt: Long? = null
) {
    val totalMinutes: Int get() = items.sumOf { it.durationMinutes }
    val exerciseCount: Int get() = items.size
}

data class RoutineItem(
    val exercise: Exercise,
    val durationMinutes: Int
)
