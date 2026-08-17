package com.example.fittrack.data.sensor

import kotlinx.coroutines.flow.Flow

interface StepSensorManager {
    fun observeSteps(): Flow<Int>
    fun isSensorAvailable(): Boolean
}