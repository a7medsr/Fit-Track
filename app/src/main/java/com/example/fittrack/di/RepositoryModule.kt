package com.example.fittrack.di

import com.example.fittrack.data.repository.StepRepositoryImpl
import com.example.fittrack.domain.repository.StepRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.example.fittrack.domain.repository.WorkoutRepository
import com.example.fittrack.data.repository.WorkoutRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindStepRepository(impl: StepRepositoryImpl): StepRepository

    @Binds
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository
}