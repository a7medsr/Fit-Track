package com.example.fittrack.domain.util

import kotlin.math.roundToInt

/**
 * Energy expenditure from the MET formula:
 *
 *     kcal/min = (MET x 3.5 x weightKg) / 200
 *
 * which is the standard conversion of a MET value into millilitres of oxygen
 * per kilogram per minute, then into kilocalories.
 */
object CalorieCalculator {

    fun kcalPerMinute(met: Double, weightKg: Int): Double = (met * 3.5 * weightKg) / 200.0

    fun caloriesBurned(met: Double, weightKg: Int, minutes: Int): Int =
        (kcalPerMinute(met, weightKg) * minutes).roundToInt()

    fun kcalPerHour(met: Double, weightKg: Int): Int = caloriesBurned(met, weightKg, 60)

    /**
     * Walking comes from the step counter rather than a MET value, so it keeps
     * its own flat per-step estimate. Defined here so the Log and Records
     * screens cannot drift apart on what a step is worth.
     */
    fun caloriesFromSteps(steps: Int): Int = (steps * KCAL_PER_STEP).toInt()

    private const val KCAL_PER_STEP = 0.04
}
