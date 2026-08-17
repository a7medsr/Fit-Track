package com.example.fittrack.di

import com.example.fittrack.data.sensor.FakeStepSensorManager
import com.example.fittrack.data.sensor.StepSensorManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SensorModule {

    @Provides
    fun provideStepSensorManager(fake: FakeStepSensorManager): StepSensorManager = fake
}