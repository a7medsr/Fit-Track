package com.example.fittrack.domain.repository

import com.example.fittrack.domain.model.DailySteps
import kotlinx.coroutines.flow.Flow
import  com.example.fittrack.domain.model.StepRecords

interface StepRepository {
    fun getStepsForDate(date: String): Flow<DailySteps?>
    fun getStepsInRange(startDate: String, endDate: String): Flow<List<DailySteps>>
    suspend fun updateSteps(date: String, stepCount: Int, sensorBaseline: Int)
    suspend fun syncTodaySteps(rawSensorValue: Int): DailySteps

    suspend fun seedMockDataIfNeeded()
    suspend fun getRecords(): StepRecords
}
