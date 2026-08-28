package com.example.fittrack.data.sync

import com.example.fittrack.domain.repository.AuthRepository
import com.example.fittrack.domain.repository.SyncRepository
import com.example.fittrack.domain.repository.WorkoutRepository
import com.example.fittrack.domain.repository.RoutineRepository
import com.example.fittrack.domain.repository.ExerciseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes local changes to the cloud without every repository having to know
 * that a cloud exists.
 *
 * It watches the Room flows the app already exposes, so no write site needed
 * changing. Emissions are debounced because one user action (logging a session)
 * fans out into several table writes, and they should ride up as one push.
 */
@Singleton
class SyncScheduler @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true

        scope.launch {
            authRepository.observeUser()
                .filterNotNull()
                .flatMapLatest {
                    combine(
                        workoutRepository.getAllWorkouts(),
                        exerciseRepository.getAllExercises(),
                        routineRepository.getAllRoutines()
                    ) { _, _, _ -> Unit }
                        // The first emission is just Room handing over current
                        // state, which the sign-in sync already pushed.
                        .drop(1)
                        .debounce(DEBOUNCE_MS)
                }
                .collect {
                    runCatching { syncRepository.pushLocalState() }
                }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 2_000L
    }
}
