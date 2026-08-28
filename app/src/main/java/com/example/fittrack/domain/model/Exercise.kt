package com.example.fittrack.domain.model

/**
 * An entry in the exercise catalogue, either bundled with the app or created
 * by the user.
 */
data class Exercise(
    val id: Long = 0,
    val name: String,
    val category: ExerciseCategory,
    /** Metabolic equivalent of task, from the Compendium of Physical Activities. */
    val met: Double,
    val icon: String,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val intensity: ExerciseIntensity? = null,
    val lastUsedAt: Long? = null
)

enum class ExerciseCategory(val storageName: String, val displayName: String) {
    CARDIO("Cardio", "Cardio"),
    STRENGTH("Strength", "Strength"),
    FLEXIBILITY("Flexibility", "Flexibility"),
    SPORTS("Sports", "Sports");

    companion object {
        fun fromStorage(value: String): ExerciseCategory? =
            entries.firstOrNull { it.storageName.equals(value, ignoreCase = true) }
    }
}

/**
 * Custom exercises do not come with a measured MET value, so the user picks an
 * intensity instead and these three stand-ins are used in the kcal formula.
 */
enum class ExerciseIntensity(val met: Double, val displayName: String) {
    LIGHT(3.0, "Light"),
    MODERATE(6.0, "Moderate"),
    INTENSE(9.0, "Intense");

    companion object {
        fun fromStorage(value: String?): ExerciseIntensity? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
