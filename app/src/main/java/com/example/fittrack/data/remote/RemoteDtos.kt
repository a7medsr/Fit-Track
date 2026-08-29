package com.example.fittrack.data.remote

/**
 * Wire shapes for the Firestore mirror.
 *
 * Every field has a default so Kotlin emits a no-arg constructor, which is what
 * Firestore's reflective deserialiser needs.
 *
 * The whole catalogue is deliberately NOT uploaded: the 100 bundled exercises
 * ship in the APK and are identical for everyone, so only the user's own
 * exercises and their favourite / last-used overrides go to the cloud.
 */
data class RemoteWorkout(
    val syncId: String = "",
    val type: String = "",
    val durationMinutes: Int = 0,
    val calories: Int = 0,
    val date: String = "",
    val notes: String? = null,
    val exerciseIcon: String? = null,
    val sessionName: String? = null
)

data class RemoteExercise(
    val syncId: String = "",
    /** Set for a bundled exercise, null for one the user created. */
    val catalogKey: String? = null,
    val name: String = "",
    val category: String = "",
    val met: Double = 0.0,
    val icon: String = "",
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val intensity: String? = null,
    val lastUsedAt: Long? = null
)

/**
 * A bundled exercise has a different local row id on every device, so a session
 * points at [exerciseCatalogKey] when it can and only falls back to
 * [exerciseSyncId] for the user's own exercises.
 */
data class RemoteRoutineItem(
    val exerciseSyncId: String = "",
    val exerciseCatalogKey: String? = null,
    val durationMinutes: Int = 0,
    val position: Int = 0
)

data class RemoteRoutine(
    val syncId: String = "",
    val name: String = "",
    val icon: String = "",
    val createdAt: Long = 0L,
    val lastUsedAt: Long? = null,
    val items: List<RemoteRoutineItem> = emptyList()
)

data class RemoteStepDay(
    val date: String = "",
    val stepCount: Int = 0
)

data class RemoteProfile(
    val weightKg: Int = 0,
    val dailyGoal: Int = 0,
    /** Where the picture lives on the VPS; the image itself is never in Firestore. */
    val avatarUrl: String? = null
)

/** One pull of everything the account has stored. */
data class RemoteSnapshot(
    val workouts: List<RemoteWorkout> = emptyList(),
    val exercises: List<RemoteExercise> = emptyList(),
    val routines: List<RemoteRoutine> = emptyList(),
    val steps: List<RemoteStepDay> = emptyList(),
    val profile: RemoteProfile? = null
) {
    val isEmpty: Boolean
        get() = workouts.isEmpty() && exercises.isEmpty() &&
            routines.isEmpty() && steps.isEmpty() && profile == null
}
