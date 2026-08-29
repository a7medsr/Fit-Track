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

    /**
     * URL of the profile picture on the VPS; the file itself is cached locally.
     *
     * Keyed per user, like the cached image files are. A single shared key
     * would hand a signed-out device the last account's picture, and would show
     * the wrong face to whoever signs in next on a shared phone.
     */
    fun getAvatarUrl(userId: String): String? =
        prefs.getString(avatarKey(userId), null)?.takeIf { it.isNotBlank() }

    fun setAvatarUrl(userId: String, url: String?) {
        prefs.edit().apply {
            if (url.isNullOrBlank()) remove(avatarKey(userId)) else putString(avatarKey(userId), url)
        }.apply()
    }

    /**
     * The URL the locally cached image was actually downloaded from.
     *
     * Upload URLs carry a ?v= stamp, so comparing this against the current URL
     * is how a device notices the picture changed elsewhere. Without it a
     * cached file is assumed current forever and the old face never updates.
     */
    fun getCachedAvatarUrl(userId: String): String? =
        prefs.getString(cachedAvatarKey(userId), null)?.takeIf { it.isNotBlank() }

    fun setCachedAvatarUrl(userId: String, url: String?) {
        prefs.edit().apply {
            if (url.isNullOrBlank()) remove(cachedAvatarKey(userId))
            else putString(cachedAvatarKey(userId), url)
        }.apply()
    }

    private fun avatarKey(userId: String) = "$KEY_AVATAR_URL_PREFIX$userId"

    private fun cachedAvatarKey(userId: String) = "$KEY_AVATAR_CACHED_PREFIX$userId"

    fun clearLastExerciseId() {
        prefs.edit().remove(KEY_LAST_EXERCISE).apply()
    }

    companion object {
        const val DEFAULT_WEIGHT_KG = 70
        const val MIN_WEIGHT_KG = 30
        const val MAX_WEIGHT_KG = 250

        private const val KEY_WEIGHT = "weight_kg"
        private const val KEY_LAST_EXERCISE = "last_exercise_id"
        private const val KEY_AVATAR_URL_PREFIX = "avatar_url_"
        private const val KEY_AVATAR_CACHED_PREFIX = "avatar_cached_"
    }
}
