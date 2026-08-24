package com.example.fittrack.ui.history

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fittrack.R
import com.example.fittrack.ui.common.NavBarHelper
import com.example.fittrack.ui.common.NavTab
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistoryActivity : AppCompatActivity() {

    private val viewModel: HistoryViewModel by viewModels()
    private val expandedDays = mutableSetOf<String>()

    private fun iconFor(type: String) = when (type) {
        "Running" -> "🏃"
        "Cycling" -> "🚴"
        "Gym" -> "🏋️"
        "Walking" -> "🚶"
        else -> "💪"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<TextView>(R.id.screenTitle).setText(R.string.history_title)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        NavBarHelper.setup(this, NavTab.HISTORY)

        val daysContainer = findViewById<LinearLayout>(R.id.daysContainer)
        val emptyState = findViewById<View>(R.id.emptyState)
        val emptyStateTitle = findViewById<TextView>(R.id.emptyStateTitle)
        val emptyStateBody = findViewById<TextView>(R.id.emptyStateBody)
        val totalBurned = findViewById<TextView>(R.id.totalBurned)
        val activeDays = findViewById<TextView>(R.id.activeDays)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            val days = state.data
                            daysContainer.removeAllViews()

                            totalBurned.text = "${days.sumOf { it.totalCalories }} kcal"
                            activeDays.text = "${days.size}"
                            // Reset the copy in case a previous emission showed the error text.
                            emptyStateTitle.setText(R.string.history_empty_title)
                            emptyStateBody.visibility = View.VISIBLE
                            emptyState.visibility = if (days.isEmpty()) View.VISIBLE else View.GONE

                            days.forEach { day ->
                                val card = layoutInflater.inflate(R.layout.item_day_summary, daysContainer, false)
                                card.findViewById<TextView>(R.id.dayName).text = day.dayName
                                card.findViewById<TextView>(R.id.dayDate).text = day.date
                                card.findViewById<TextView>(R.id.dayCalories).text = "${day.totalCalories} kcal"
                                card.findViewById<TextView>(R.id.dayActivityCount).text =
                                    "${day.workouts.size} ${if (day.workouts.size == 1) "activity" else "activities"}"

                                val details = card.findViewById<LinearLayout>(R.id.detailsContainer)
                                val arrow = card.findViewById<ImageView>(R.id.expandArrow)

                                fun renderDetails() {
                                    details.removeAllViews()
                                    day.workouts.forEach { workout ->
                                        val row = layoutInflater.inflate(R.layout.item_workout_detail, details, false)
                                        row.findViewById<TextView>(R.id.detailIcon).text = iconFor(workout.type)
                                        row.findViewById<TextView>(R.id.detailType).text = workout.type
                                        row.findViewById<TextView>(R.id.detailStats).text =
                                            "${workout.durationMinutes} min · ${workout.calories} kcal"

                                        val notesView = row.findViewById<TextView>(R.id.detailNotes)
                                        if (!workout.notes.isNullOrBlank()) {
                                            notesView.text = "\"${workout.notes}\""
                                            notesView.visibility = View.VISIBLE
                                        }

                                        row.findViewById<View>(R.id.deleteBtn).setOnClickListener {
                                            viewModel.deleteWorkout(workout)
                                        }
                                        details.addView(row)
                                    }
                                }

                                val isExpanded = expandedDays.contains(day.date)
                                details.visibility = if (isExpanded) View.VISIBLE else View.GONE
                                arrow.rotation = if (isExpanded) 180f else 0f
                                if (isExpanded) renderDetails()

                                card.setOnClickListener {
                                    if (expandedDays.contains(day.date)) {
                                        expandedDays.remove(day.date)
                                        details.visibility = View.GONE
                                        arrow.animate().rotation(0f).setDuration(180L).start()
                                    } else {
                                        expandedDays.add(day.date)
                                        renderDetails()
                                        details.visibility = View.VISIBLE
                                        arrow.animate().rotation(180f).setDuration(180L).start()
                                    }
                                }

                                daysContainer.addView(card)
                            }
                        }
                        is UiState.Error -> {
                            emptyStateTitle.setText(R.string.history_error)
                            emptyStateBody.visibility = View.GONE
                            emptyState.visibility = View.VISIBLE
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}