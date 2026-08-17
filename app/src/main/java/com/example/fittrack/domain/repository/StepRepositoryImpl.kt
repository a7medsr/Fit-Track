package com.example.fittrack.data.repository

import com.example.fittrack.data.local.StepDao
import com.example.fittrack.data.local.StepEntity
import com.example.fittrack.domain.model.DailySteps
import com.example.fittrack.domain.repository.StepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate
class StepRepositoryImpl @Inject constructor(
    private val stepDao: StepDao
) : StepRepository {

    override fun getStepsForDate(date: String): Flow<DailySteps?> {
        return stepDao.getStepsForDate(date).map { entity ->
            entity?.let { DailySteps(it.date, it.stepCount) }
        }
    }
    override suspend fun seedMockDataIfNeeded() {
        if (stepDao.getRowCount() > 5) return

        val today = LocalDate.now()
        for (i in 1..60) {
            val date = today.minusDays(i.toLong())
            val randomSteps = (3000..15000).random()
            stepDao.upsert(
                StepEntity(
                    date = date.toString(),
                    stepCount = randomSteps,
                    sensorBaseline = randomSteps,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }
    override suspend fun syncTodaySteps(rawSensorValue: Int): DailySteps {
        val today = LocalDate.now().toString()
        val existing = stepDao.getStepsForDate(today).firstOrNull()

        val baseline = when {
            existing == null -> rawSensorValue
            rawSensorValue < existing.sensorBaseline -> rawSensorValue
            else -> existing.sensorBaseline
        }

        val computedSteps = rawSensorValue - baseline

        stepDao.upsert(
            StepEntity(
                date = today,
                stepCount = computedSteps,
                sensorBaseline = baseline,
                lastUpdated = System.currentTimeMillis()
            )
        )

        return DailySteps(today, computedSteps)
    }

    override fun getStepsInRange(startDate: String, endDate: String): Flow<List<DailySteps>> {
        return stepDao.getStepsInRange(startDate, endDate).map { list ->
            list.map { DailySteps(it.date, it.stepCount) }
        }
    }

    override suspend fun updateSteps(date: String, stepCount: Int, sensorBaseline: Int) {
        stepDao.upsert(
            StepEntity(
                date = date,
                stepCount = stepCount,
                sensorBaseline = sensorBaseline,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }
}