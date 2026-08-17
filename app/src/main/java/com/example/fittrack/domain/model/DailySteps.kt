package com.example.fittrack.domain.model

data class DailySteps(
    val date: String,
    val stepCount: Int,
    val goal: Int = 10000
)