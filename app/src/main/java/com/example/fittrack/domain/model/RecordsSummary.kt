package com.example.fittrack.domain.model

import java.time.LocalDate

/** Everything the Records screen puts on show, derived from steps and workouts. */
data class RecordsSummary(
    // Personal bests
    val bestStepDay: DayRecord?,
    val bestCalorieDay: DayRecord?,
    val bestStepWeek: WeekRecord?,
    val longestWorkout: WorkoutRecord?,
    val hardestWorkout: WorkoutRecord?,

    // Consistency
    val currentStreak: Int,
    val longestStreak: Int,
    val activeDays: Int,

    // Lifetime totals
    val totalCalories: Int,
    val totalActiveMinutes: Int,
    val totalWorkouts: Int,
    val totalSteps: Int,
    val trainingSince: LocalDate?,

    // Habits
    val topExercises: List<ExerciseTally>,
    val busiestWeekday: WeekdayRecord?
)

data class DayRecord(val value: Int, val date: LocalDate)

data class WeekRecord(val total: Int, val start: LocalDate, val end: LocalDate)

data class WorkoutRecord(
    val name: String,
    val icon: String?,
    val minutes: Int,
    val calories: Int,
    val date: LocalDate
)

data class ExerciseTally(
    val name: String,
    val icon: String?,
    val count: Int,
    val minutes: Int,
    val calories: Int
)

data class WeekdayRecord(val dayName: String, val workouts: Int)
