package com.example.fittrack.domain.repository

import com.example.fittrack.domain.model.Routine
import com.example.fittrack.domain.model.RoutineItem
import kotlinx.coroutines.flow.Flow

interface RoutineRepository {
    fun getAllRoutines(): Flow<List<Routine>>
    suspend fun getRoutine(id: Long): Routine?
    suspend fun createRoutine(name: String, icon: String, items: List<RoutineItem>): Long
    suspend fun updateRoutine(id: Long, name: String, icon: String, items: List<RoutineItem>)
    suspend fun deleteRoutine(id: Long)
    suspend fun markUsed(id: Long)
}
