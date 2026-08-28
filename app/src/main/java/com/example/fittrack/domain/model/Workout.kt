package com.example.fittrack.domain.model

data class Workout(
    val id: Long = 0,
    val type: String,
    val durationMinutes: Int,
    val calories: Int,
    val date: String,
    val notes: String? = null,
    val exerciseIcon: String? = null,
    val sessionName: String? = null
)
