package com.example.fittrack

import com.example.fittrack.domain.model.ExerciseIntensity
import com.example.fittrack.domain.util.CalorieCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class CalorieCalculatorTest {

    @Test
    fun `kcal per minute follows the MET formula`() {
        // (9.8 x 3.5 x 70) / 200 = 12.005
        assertEquals(12.005, CalorieCalculator.kcalPerMinute(met = 9.8, weightKg = 70), 0.0001)
    }

    @Test
    fun `calories scale with duration`() {
        assertEquals(360, CalorieCalculator.caloriesBurned(met = 9.8, weightKg = 70, minutes = 30))
        assertEquals(720, CalorieCalculator.caloriesBurned(met = 9.8, weightKg = 70, minutes = 60))
    }

    @Test
    fun `calories scale with body weight`() {
        val light = CalorieCalculator.caloriesBurned(met = 6.0, weightKg = 60, minutes = 45)
        val heavy = CalorieCalculator.caloriesBurned(met = 6.0, weightKg = 90, minutes = 45)
        assertEquals(284, light) // 283.5 rounds up
        assertEquals(425, heavy)
    }

    @Test
    fun `kcal per hour is the sixty minute case`() {
        assertEquals(
            CalorieCalculator.caloriesBurned(met = 3.0, weightKg = 70, minutes = 60),
            CalorieCalculator.kcalPerHour(met = 3.0, weightKg = 70)
        )
    }

    @Test
    fun `custom intensities map to three six and nine METs`() {
        assertEquals(3.0, ExerciseIntensity.LIGHT.met, 0.0)
        assertEquals(6.0, ExerciseIntensity.MODERATE.met, 0.0)
        assertEquals(9.0, ExerciseIntensity.INTENSE.met, 0.0)
    }
}
