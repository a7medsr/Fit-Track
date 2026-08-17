package com.example.fittrack.data.sensor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class FakeStepSensorManager @Inject constructor() : StepSensorManager {

    override fun isSensorAvailable(): Boolean = true

    override fun observeSteps(): Flow<Int> = flow {
        var simulatedSteps = 4200
        while (true) {
            emit(simulatedSteps)
            delay(3000)
            simulatedSteps += (5..20).random()
        }
    }
}