package com.example.fittrack.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes the per-user mirror at `users/{uid}/data/{collection}`.
 *
 * Each collection is a single document holding a list rather than one document
 * per record. That keeps a sync to five writes instead of hundreds, and makes a
 * push a whole-list replace, so deletions propagate without needing tombstones.
 * The trade is Firestore's 1 MiB document ceiling, which at roughly 150 bytes a
 * workout is somewhere north of 6000 workouts.
 */
@Singleton
class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun pull(uid: String): RemoteSnapshot {
        val root = userData(uid)

        val workouts = root.document(DOC_WORKOUTS).get().await()
            .toList(RemoteWorkout::class.java)
        val exercises = root.document(DOC_EXERCISES).get().await()
            .toList(RemoteExercise::class.java)
        val steps = root.document(DOC_STEPS).get().await()
            .toList(RemoteStepDay::class.java)
        val routines = root.document(DOC_ROUTINES).get().await()
            .toRoutines()
        val profile = root.document(DOC_PROFILE).get().await()
            .takeIf { it.exists() }
            ?.toObject(RemoteProfile::class.java)

        return RemoteSnapshot(workouts, exercises, routines, steps, profile)
    }

    suspend fun push(uid: String, snapshot: RemoteSnapshot) {
        val root = userData(uid)
        val batch = firestore.batch()

        batch.set(root.document(DOC_WORKOUTS), wrap(snapshot.workouts))
        batch.set(root.document(DOC_EXERCISES), wrap(snapshot.exercises))
        batch.set(root.document(DOC_ROUTINES), wrap(snapshot.routines))
        batch.set(root.document(DOC_STEPS), wrap(snapshot.steps))
        snapshot.profile?.let { batch.set(root.document(DOC_PROFILE), it) }

        batch.commit().await()
    }

    private fun userData(uid: String) =
        firestore.collection(COLLECTION_USERS).document(uid).collection(COLLECTION_DATA)

    private fun wrap(items: List<Any>) = mapOf(
        FIELD_ITEMS to items,
        FIELD_UPDATED_AT to System.currentTimeMillis()
    )

    private fun <T> com.google.firebase.firestore.DocumentSnapshot.toList(
        type: Class<T>
    ): List<T> {
        if (!exists()) return emptyList()
        @Suppress("UNCHECKED_CAST")
        val raw = get(FIELD_ITEMS) as? List<Map<String, Any?>> ?: return emptyList()
        return raw.mapNotNull { item ->
            try {
                convert(item, type)
            } catch (e: Exception) {
                null // one malformed record must not sink the whole pull
            }
        }
    }

    /**
     * Firestore only maps nested objects automatically at the document root, so
     * the list entries are converted by hand.
     */
    private fun <T> convert(map: Map<String, Any?>, type: Class<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return when (type) {
            RemoteWorkout::class.java -> RemoteWorkout(
                syncId = map.str("syncId"),
                type = map.str("type"),
                durationMinutes = map.int("durationMinutes"),
                calories = map.int("calories"),
                date = map.str("date"),
                notes = map.strOrNull("notes"),
                exerciseIcon = map.strOrNull("exerciseIcon"),
                sessionName = map.strOrNull("sessionName")
            ) as T
            RemoteExercise::class.java -> RemoteExercise(
                syncId = map.str("syncId"),
                catalogKey = map.strOrNull("catalogKey"),
                name = map.str("name"),
                category = map.str("category"),
                met = (map["met"] as? Number)?.toDouble() ?: 0.0,
                icon = map.str("icon"),
                isCustom = map["isCustom"] as? Boolean ?: false,
                isFavorite = map["isFavorite"] as? Boolean ?: false,
                intensity = map.strOrNull("intensity"),
                lastUsedAt = (map["lastUsedAt"] as? Number)?.toLong()
            ) as T
            RemoteStepDay::class.java -> RemoteStepDay(
                date = map.str("date"),
                stepCount = map.int("stepCount")
            ) as T
            else -> null
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toRoutines(): List<RemoteRoutine> {
        if (!exists()) return emptyList()
        @Suppress("UNCHECKED_CAST")
        val raw = get(FIELD_ITEMS) as? List<Map<String, Any?>> ?: return emptyList()
        return raw.mapNotNull { routine ->
            try {
                @Suppress("UNCHECKED_CAST")
                val items = routine["items"] as? List<Map<String, Any?>> ?: emptyList()
                RemoteRoutine(
                    syncId = routine.str("syncId"),
                    name = routine.str("name"),
                    icon = routine.str("icon"),
                    createdAt = (routine["createdAt"] as? Number)?.toLong() ?: 0L,
                    lastUsedAt = (routine["lastUsedAt"] as? Number)?.toLong(),
                    items = items.map {
                        RemoteRoutineItem(
                            exerciseSyncId = it.str("exerciseSyncId"),
                            exerciseCatalogKey = it.strOrNull("exerciseCatalogKey"),
                            durationMinutes = it.int("durationMinutes"),
                            position = it.int("position")
                        )
                    }
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun Map<String, Any?>.str(key: String): String = this[key] as? String ?: ""
    private fun Map<String, Any?>.strOrNull(key: String): String? = this[key] as? String
    private fun Map<String, Any?>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: 0

    private companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_DATA = "data"
        const val DOC_WORKOUTS = "workouts"
        const val DOC_EXERCISES = "exercises"
        const val DOC_ROUTINES = "routines"
        const val DOC_STEPS = "steps"
        const val DOC_PROFILE = "profile"
        const val FIELD_ITEMS = "items"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}
