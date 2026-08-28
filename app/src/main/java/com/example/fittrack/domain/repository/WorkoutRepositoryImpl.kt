package com.example.fittrack.data.repository

import com.example.fittrack.data.local.WorkoutDao
import com.example.fittrack.data.local.WorkoutEntity
import com.example.fittrack.domain.model.Workout
import com.example.fittrack.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class WorkoutRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao
) : WorkoutRepository {

    override fun getAllWorkouts(): Flow<List<Workout>> {
        return workoutDao.getAllWorkouts().map { list ->
            list.map { entity ->
                Workout(
                    id = entity.id,
                    type = entity.type,
                    durationMinutes = entity.durationMinutes,
                    calories = entity.calories,
                    date = entity.date,
                    notes = entity.notes,
                    exerciseIcon = entity.exerciseIcon,
                    sessionName = entity.sessionName
                )
            }
        }
    }

    override suspend fun addWorkout(workout: Workout) {
        workoutDao.insert(
            WorkoutEntity(
                type = workout.type,
                durationMinutes = workout.durationMinutes,
                calories = workout.calories,
                date = workout.date,
                notes = workout.notes,
                exerciseIcon = workout.exerciseIcon,
                sessionName = workout.sessionName
            )
        )
    }

    override suspend fun addWorkouts(workouts: List<Workout>) {
        workoutDao.insertAll(
            workouts.map { workout ->
                WorkoutEntity(
                    type = workout.type,
                    durationMinutes = workout.durationMinutes,
                    calories = workout.calories,
                    date = workout.date,
                    notes = workout.notes,
                    exerciseIcon = workout.exerciseIcon,
                    sessionName = workout.sessionName
                )
            }
        )
    }

    override suspend fun updateWorkout(workout: Workout) {
        workoutDao.update(
            WorkoutEntity(
                id = workout.id,
                type = workout.type,
                durationMinutes = workout.durationMinutes,
                calories = workout.calories,
                date = workout.date,
                notes = workout.notes,
                exerciseIcon = workout.exerciseIcon,
                sessionName = workout.sessionName
            )
        )
    }

    override suspend fun deleteWorkout(workout: Workout) {
        workoutDao.delete(
            WorkoutEntity(
                id = workout.id,
                type = workout.type,
                durationMinutes = workout.durationMinutes,
                calories = workout.calories,
                date = workout.date,
                notes = workout.notes,
                exerciseIcon = workout.exerciseIcon,
                sessionName = workout.sessionName
            )
        )
    }

    override suspend fun seedMockWorkoutsIfNeeded() {
        if (workoutDao.getCount() > 3) return

        val types = listOf("Running", "Cycling", "Gym", "Walking")
        val today = LocalDate.now()

        for (i in 0..14) {
            if ((0..2).random() == 0) continue // skip some days, so it's not every single day
            val date = today.minusDays(i.toLong())
            workoutDao.insert(
                WorkoutEntity(
                    type = types.random(),
                    durationMinutes = (20..60).random(),
                    calories = (150..500).random(),
                    date = date.toString(),
                    notes = if ((0..1).random() == 0) "Felt good today" else null
                )
            )
        }
    }
}