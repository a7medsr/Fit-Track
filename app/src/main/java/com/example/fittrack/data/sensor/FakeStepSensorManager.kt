package com.example.fittrack.data.sensor

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class FakeStepSensorManager @Inject constructor(
    @ApplicationContext private val context: Context
) : StepSensorManager {

    private val prefs = context.getSharedPreferences("fake_sensor_prefs", Context.MODE_PRIVATE)

    override fun isSensorAvailable(): Boolean = true

    override fun observeSteps(): Flow<Int> = flow {
        var simulatedSteps = prefs.getInt("simulated_steps", 4200)
        while (true) {
            emit(simulatedSteps)
            prefs.edit().putInt("simulated_steps", simulatedSteps).apply()
            delay(3000)
            simulatedSteps += (5..20).random()
        }
    }
}