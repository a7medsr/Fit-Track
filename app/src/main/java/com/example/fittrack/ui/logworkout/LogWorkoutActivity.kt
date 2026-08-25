package com.example.fittrack.ui.logworkout

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fittrack.R
import com.example.fittrack.ui.common.NavBarHelper
import com.example.fittrack.ui.common.NavTab
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

import com.example.fittrack.ui.history.HistoryActivity
@AndroidEntryPoint
class LogWorkoutActivity : AppCompatActivity() {

    private val viewModel: LogWorkoutViewModel by viewModels()

    private val activities = listOf(
        Triple("Running", "🏃", 30),
        Triple("Cycling", "🚴", 30),
        Triple("Gym", "🏋️", 45)
    )

    private val durations = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_workout)

        findViewById<TextView>(R.id.screenTitle).setText(R.string.log_title)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        NavBarHelper.setup(this, NavTab.LOG)

        val container = findViewById<LinearLayout>(R.id.activityCardsContainer)
        val notesInput = findViewById<EditText>(R.id.notesInput)
        val todayTotalCalories = findViewById<TextView>(R.id.todayTotalCalories)
        val todayTotalSummary = findViewById<TextView>(R.id.todayTotalSummary)

        findViewById<View>(R.id.viewHistoryBtn).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        activities.forEach { (type, icon, defaultMin) ->
            durations[type] = defaultMin

            val card = layoutInflater.inflate(R.layout.item_activity_log, container, false)
            card.findViewById<TextView>(R.id.activityIcon).text = icon
            card.findViewById<TextView>(R.id.activityName).text = type

            val durationValue = card.findViewById<TextView>(R.id.durationValue)
            val caloriesText = card.findViewById<TextView>(R.id.activityCalories)

            fun refresh() {
                val mins = durations[type] ?: 0
                durationValue.text = "$mins min"
                caloriesText.text = "≈ ${viewModel.estimateCalories(type, mins)} kcal"
            }

            card.findViewById<TextView>(R.id.durationPlus).setOnClickListener {
                durations[type] = (durations[type] ?: 0) + 5
                refresh()
            }
            card.findViewById<TextView>(R.id.durationMinus).setOnClickListener {
                val current = durations[type] ?: 0
                if (current > 5) durations[type] = current - 5
                refresh()
            }

            card.findViewById<TextView>(R.id.submitActivityBtn).setOnClickListener {
                viewModel.submitActivity(type, durations[type] ?: 0, notesInput.text.toString())
                Toast.makeText(this, "$type logged!", Toast.LENGTH_SHORT).show()
            }

            refresh()
            container.addView(card)
        }

        val autoWalkCard = layoutInflater.inflate(R.layout.item_auto_walking, container, false)
        container.addView(autoWalkCard)
        val autoWalkSteps = autoWalkCard.findViewById<TextView>(R.id.autoWalkSteps)
        val autoWalkCalories = autoWalkCard.findViewById<TextView>(R.id.autoWalkCalories)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.todaySteps.collect { steps ->
                    autoWalkSteps.text = "$steps steps today"
                    autoWalkCalories.text = "≈ ${viewModel.caloriesFromSteps(steps)} kcal"
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.todayWorkouts.collect { workouts ->
                    val loggedTotal = workouts.sumOf { it.calories }
                    val walkingCalories = viewModel.caloriesFromSteps(viewModel.todaySteps.value)
                    val total = loggedTotal + walkingCalories

                    todayTotalCalories.text = "$total kcal"
                    todayTotalSummary.text = if (workouts.isEmpty()) {
                        "Walking only · $walkingCalories kcal"
                    } else {
                        workouts.groupBy { it.type }
                            .map { (type, list) -> "$type ×${list.size}" }
                            .joinToString(" · ") + " · Walking"
                    }
                    for (i in activities.indices) {
                        val card = container.getChildAt(i)
                        val type = activities[i].first
                        val count = workouts.count { it.type == type }
                        card.findViewById<TextView>(R.id.activityLoggedToday).text =
                            if (count > 0) "✓ $count today" else ""
                    }
                }
            }
        }
    }
}