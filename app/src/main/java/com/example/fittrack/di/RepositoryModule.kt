package com.example.fittrack.di

import com.example.fittrack.data.repository.ExerciseRepositoryImpl
import com.example.fittrack.data.auth.AuthRepositoryImpl
import com.example.fittrack.data.repository.ChatRepositoryImpl
import com.example.fittrack.data.repository.RoutineRepositoryImpl
import com.example.fittrack.data.repository.StepRepositoryImpl
import com.example.fittrack.domain.repository.ExerciseRepository
import com.example.fittrack.data.sync.SyncRepositoryImpl
import com.example.fittrack.domain.repository.AuthRepository
import com.example.fittrack.domain.repository.ChatRepository
import com.example.fittrack.domain.repository.RoutineRepository
import com.example.fittrack.domain.repository.SyncRepository
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

    @Binds
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    abstract fun bindRoutineRepository(impl: RoutineRepositoryImpl): RoutineRepository

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
}
