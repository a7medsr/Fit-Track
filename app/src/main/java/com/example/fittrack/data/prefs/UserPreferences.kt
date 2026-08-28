package com.example.fittrack.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Body weight, which the MET calorie formula needs, plus the exercise the Log
 * screen was last left on so reopening it lands where the user left off.
 */
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    fun getWeightKg(): Int = prefs.getInt(KEY_WEIGHT, DEFAULT_WEIGHT_KG)

    fun setWeightKg(value: Int) {
        prefs.edit().putInt(KEY_WEIGHT, value.coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG)).apply()
    }

    /** Returns null when nothing has been picked yet. */
    fun getLastExerciseId(): Long? =
        prefs.getLong(KEY_LAST_EXERCISE, 0L).takeIf { it > 0L }

    fun setLastExerciseId(id: Long) {
        prefs.edit().putLong(KEY_LAST_EXERCISE, id).apply()
    }

    fun clearLastExerciseId() {
        prefs.edit().remove(KEY_LAST_EXERCISE).apply()
    }

    companion object {
        const val DEFAULT_WEIGHT_KG = 70
        const val MIN_WEIGHT_KG = 30
        const val MAX_WEIGHT_KG = 250

        private const val KEY_WEIGHT = "weight_kg"
        private const val KEY_LAST_EXERCISE = "last_exercise_id"
    }
}
