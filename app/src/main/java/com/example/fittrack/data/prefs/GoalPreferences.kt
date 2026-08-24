package com.example.fittrack.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GoalPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("goal_prefs", Context.MODE_PRIVATE)

    fun getGoal(): Int = prefs.getInt("daily_goal", 10000)

    fun setGoal(value: Int) {
        prefs.edit().putInt("daily_goal", value).apply()
    }
}