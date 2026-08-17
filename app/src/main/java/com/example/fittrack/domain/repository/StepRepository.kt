package com.example.fittrack.domain.repository

import com.example.fittrack.domain.model.DailySteps
import kotlinx.coroutines.flow.Flow

interface StepRepository {
    fun getStepsForDate(date: String): Flow<DailySteps?>
    fun getStepsInRange(startDate: String, endDate: String): Flow<List<DailySteps>>
    suspend fun updateSteps(date: String, stepCount: Int, sensorBaseline: Int)
    suspend fun syncTodaySteps(rawSensorValue: Int): DailySteps
}