package com.example.fittrack.data.seed

import android.content.Context
import com.example.fittrack.data.local.ExerciseEntity
import com.example.fittrack.domain.model.ExerciseCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject

/**
 * Reads the bundled catalogue out of `assets/exercises.json`. Everything is
 * offline: the file ships with the APK and is parsed with org.json, so there is
 * no network call and no extra JSON dependency.
 */
class ExerciseSeeder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun loadCatalog(): List<ExerciseEntity> {
        val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val array = JSONObject(raw).getJSONArray("exercises")

        return (0 until array.length()).mapNotNull { i ->
            val item = array.getJSONObject(i)
            val category = ExerciseCategory.fromStorage(item.getString("category"))
                ?: return@mapNotNull null // unknown category: skip rather than crash on a bad edit

            ExerciseEntity(
                catalogKey = item.getString("key"),
                name = item.getString("name"),
                category = category.storageName,
                met = item.getDouble("met"),
                icon = item.getString("icon"),
                isCustom = false,
                isFavorite = false,
                intensity = null,
                lastUsedAt = null
            )
        }
    }

    private companion object {
        const val ASSET_NAME = "exercises.json"
    }
}
