package com.example.fittrack.data.sync

import com.example.fittrack.data.local.ExerciseDao
import com.example.fittrack.data.local.ExerciseEntity
import com.example.fittrack.data.local.RoutineDao
import com.example.fittrack.data.local.RoutineEntity
import com.example.fittrack.data.local.RoutineExerciseEntity
import com.example.fittrack.data.local.StepDao
import com.example.fittrack.data.local.StepEntity
import com.example.fittrack.data.local.WorkoutDao
import com.example.fittrack.data.local.WorkoutEntity
import com.example.fittrack.data.prefs.GoalPreferences
import com.example.fittrack.data.prefs.UserPreferences
import com.example.fittrack.data.remote.FirestoreDataSource
import com.example.fittrack.data.remote.RemoteExercise
import com.example.fittrack.data.remote.RemoteProfile
import com.example.fittrack.data.remote.RemoteRoutine
import com.example.fittrack.data.remote.RemoteRoutineItem
import com.example.fittrack.data.remote.RemoteSnapshot
import com.example.fittrack.data.remote.RemoteStepDay
import com.example.fittrack.data.remote.RemoteWorkout
import com.example.fittrack.domain.repository.AuthRepository
import com.example.fittrack.domain.repository.SyncRepository
import com.example.fittrack.domain.repository.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the local Room database and the per-user Firestore mirror in step.
 *
 * Room stays the source of truth so the app works with no signal. A sign-in
 * merges the two by [syncId] -- a union, never a replace, so nothing logged
 * offline is lost -- and every later change pushes the whole local state up.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val remote: FirestoreDataSource,
    private val authRepository: AuthRepository,
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val routineDao: RoutineDao,
    private val stepDao: StepDao,
    private val userPreferences: UserPreferences,
    private val goalPreferences: GoalPreferences
) : SyncRepository {

    private val _lastPullAt = MutableStateFlow(0L)
    override val lastPullAt: StateFlow<Long> = _lastPullAt.asStateFlow()

    override suspend fun pullRemoteState(): SyncResult {
        val uid = authRepository.currentUser?.uid ?: return SyncResult.NotSignedIn
        return try {
            val cloud = remote.pull(uid)
            val pulled = if (cloud.isEmpty) 0 else applyRemote(cloud, uid)
            _lastPullAt.value = System.currentTimeMillis()
            SyncResult.Success(pulled = pulled, pushed = 0)
        } catch (e: Exception) {
            SyncResult.Failure(e.message ?: "Sync failed")
        }
    }

    override suspend fun syncOnSignIn(): SyncResult {
        val uid = authRepository.currentUser?.uid ?: return SyncResult.NotSignedIn
        return try {
            val cloud = remote.pull(uid)
            val pulled = if (cloud.isEmpty) 0 else applyRemote(cloud, uid)
            _lastPullAt.value = System.currentTimeMillis()
            val local = collectLocal(uid)
            remote.push(uid, local)
            SyncResult.Success(pulled = pulled, pushed = local.workouts.size)
        } catch (e: Exception) {
            SyncResult.Failure(e.message ?: "Sync failed")
        }
    }

    override suspend fun pushLocalState(): SyncResult {
        val uid = authRepository.currentUser?.uid ?: return SyncResult.NotSignedIn
        return try {
            val local = collectLocal(uid)
            remote.push(uid, local)
            SyncResult.Success(pulled = 0, pushed = local.workouts.size)
        } catch (e: Exception) {
            SyncResult.Failure(e.message ?: "Sync failed")
        }
    }

    // ---------------------------------------------------------------- pull

    /** Returns how many records were new to this device. */
    private suspend fun applyRemote(cloud: RemoteSnapshot, uid: String): Int {
        var added = 0

        val knownWorkouts = workoutDao.getAllSyncIds().toSet()
        cloud.workouts.filter { it.syncId.isNotBlank() && it.syncId !in knownWorkouts }
            .forEach { w ->
                workoutDao.insert(
                    WorkoutEntity(
                        type = w.type,
                        durationMinutes = w.durationMinutes,
                        calories = w.calories,
                        date = w.date,
                        notes = w.notes,
                        exerciseIcon = w.exerciseIcon,
                        sessionName = w.sessionName,
                        syncId = w.syncId
                    )
                )
                added++
            }

        cloud.exercises.forEach { remoteExercise ->
            if (applyRemoteExercise(remoteExercise)) added++
        }

        cloud.routines.forEach { remoteRoutine ->
            if (applyRemoteRoutine(remoteRoutine)) added++
        }

        // Steps are keyed by date, so an upsert is the whole merge. The higher
        // count wins: a device that saw more of the day is the better record.
        cloud.steps.forEach { day ->
            val existing = stepDao.getAllOnce().firstOrNull { it.date == day.date }
            if (existing == null) {
                stepDao.upsert(
                    StepEntity(
                        date = day.date,
                        stepCount = day.stepCount,
                        sensorBaseline = day.stepCount,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
                added++
            } else if (day.stepCount > existing.stepCount) {
                stepDao.upsert(existing.copy(stepCount = day.stepCount))
            }
        }

        cloud.profile?.let { profile ->
            if (profile.weightKg > 0) userPreferences.setWeightKg(profile.weightKg)
            if (profile.dailyGoal > 0) goalPreferences.setGoal(profile.dailyGoal)
            // A second device learns where the picture is from here, then
            // downloads it into its own local cache on next launch.
            if (!profile.avatarUrl.isNullOrBlank()) {
                userPreferences.setAvatarUrl(uid, profile.avatarUrl)
            }
        }

        return added
    }

    /**
     * A bundled exercise already exists locally, so only the user's overrides
     * are applied. One the user created is inserted outright.
     */
    private suspend fun applyRemoteExercise(remoteExercise: RemoteExercise): Boolean {
        if (!remoteExercise.catalogKey.isNullOrBlank()) {
            val local = exerciseDao.getByCatalogKey(remoteExercise.catalogKey) ?: return false
            val mergedLastUsed = maxOfNullable(local.lastUsedAt, remoteExercise.lastUsedAt)
            exerciseDao.update(
                local.copy(
                    isFavorite = local.isFavorite || remoteExercise.isFavorite,
                    lastUsedAt = mergedLastUsed
                )
            )
            return false
        }

        if (remoteExercise.syncId.isBlank()) return false
        if (exerciseDao.getBySyncId(remoteExercise.syncId) != null) return false

        exerciseDao.insert(
            ExerciseEntity(
                catalogKey = null,
                name = remoteExercise.name,
                category = remoteExercise.category,
                met = remoteExercise.met,
                icon = remoteExercise.icon,
                isCustom = true,
                isFavorite = remoteExercise.isFavorite,
                intensity = remoteExercise.intensity,
                lastUsedAt = remoteExercise.lastUsedAt,
                syncId = remoteExercise.syncId
            )
        )
        return true
    }

    private suspend fun applyRemoteRoutine(remoteRoutine: RemoteRoutine): Boolean {
        if (remoteRoutine.syncId.isBlank()) return false
        if (routineDao.getBySyncId(remoteRoutine.syncId) != null) return false

        val routineId = routineDao.insertRoutine(
            RoutineEntity(
                name = remoteRoutine.name,
                icon = remoteRoutine.icon,
                createdAt = remoteRoutine.createdAt.takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
                lastUsedAt = remoteRoutine.lastUsedAt,
                syncId = remoteRoutine.syncId
            )
        )

        // Resolve each item back to a local exercise row. Anything that cannot
        // be resolved is dropped rather than left dangling, which the foreign
        // key would reject anyway.
        val items = remoteRoutine.items.mapNotNull { item ->
            val exercise = when {
                !item.exerciseCatalogKey.isNullOrBlank() ->
                    exerciseDao.getByCatalogKey(item.exerciseCatalogKey)
                item.exerciseSyncId.isNotBlank() ->
                    exerciseDao.getBySyncId(item.exerciseSyncId)
                else -> null
            } ?: return@mapNotNull null

            RoutineExerciseEntity(
                routineId = routineId,
                exerciseId = exercise.id,
                durationMinutes = item.durationMinutes,
                position = item.position
            )
        }
        if (items.isNotEmpty()) routineDao.insertItems(items)
        return true
    }

    // ---------------------------------------------------------------- push

    private suspend fun collectLocal(uid: String): RemoteSnapshot {
        val exercises = exerciseDao.getAllOnce()
        val byId = exercises.associateBy { it.id }

        return RemoteSnapshot(
            workouts = workoutDao.getAllOnce().map {
                RemoteWorkout(
                    syncId = it.syncId,
                    type = it.type,
                    durationMinutes = it.durationMinutes,
                    calories = it.calories,
                    date = it.date,
                    notes = it.notes,
                    exerciseIcon = it.exerciseIcon,
                    sessionName = it.sessionName
                )
            },
            // Bundled exercises only travel when the user has marked or used
            // them; the catalogue itself ships with the app.
            exercises = exercises
                .filter { it.isCustom || it.isFavorite || it.lastUsedAt != null }
                .map {
                    RemoteExercise(
                        syncId = it.syncId,
                        catalogKey = it.catalogKey,
                        name = it.name,
                        category = it.category,
                        met = it.met,
                        icon = it.icon,
                        isCustom = it.isCustom,
                        isFavorite = it.isFavorite,
                        intensity = it.intensity,
                        lastUsedAt = it.lastUsedAt
                    )
                },
            routines = routineDao.getAllOnce().map { routine ->
                RemoteRoutine(
                    syncId = routine.routine.syncId,
                    name = routine.routine.name,
                    icon = routine.routine.icon,
                    createdAt = routine.routine.createdAt,
                    lastUsedAt = routine.routine.lastUsedAt,
                    items = routine.items.sortedBy { it.item.position }.mapNotNull { row ->
                        val exercise = row.exercise ?: byId[row.item.exerciseId]
                        ?: return@mapNotNull null
                        RemoteRoutineItem(
                            exerciseSyncId = exercise.syncId,
                            exerciseCatalogKey = exercise.catalogKey,
                            durationMinutes = row.item.durationMinutes,
                            position = row.item.position
                        )
                    }
                )
            },
            steps = stepDao.getAllOnce().map { RemoteStepDay(it.date, it.stepCount) },
            profile = RemoteProfile(
                weightKg = userPreferences.getWeightKg(),
                dailyGoal = goalPreferences.getGoal(),
                // Only the URL travels. The image itself lives in a folder on
                // the VPS; Firestore never carries the bytes.
                avatarUrl = userPreferences.getAvatarUrl(uid)
            )
        )
    }

    private fun maxOfNullable(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }
}
