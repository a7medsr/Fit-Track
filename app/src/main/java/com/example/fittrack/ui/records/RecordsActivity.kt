package com.example.fittrack.ui.records

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fittrack.R
import com.example.fittrack.domain.model.RecordsSummary
import com.example.fittrack.ui.common.NavBarHelper
import com.example.fittrack.ui.common.NavTab
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@AndroidEntryPoint
class RecordsActivity : AppCompatActivity() {

    private val viewModel: RecordsViewModel by viewModels()

    private lateinit var scroll: View
    private lateinit var stateView: View
    private lateinit var stateTitle: TextView
    private lateinit var stateBody: TextView
    private lateinit var totalCaloriesValue: TextView
    private lateinit var totalCaloriesCaption: TextView
    private lateinit var statTiles: LinearLayout
    private lateinit var bestsCard: LinearLayout
    private lateinit var lifetimeCard: LinearLayout
    private lateinit var topExercisesTitle: View
    private lateinit var topExercisesCard: LinearLayout

    private val numbers: NumberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())
    private val dayFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
    private val shortDayFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)

        findViewById<TextView>(R.id.screenTitle).setText(R.string.records_title)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        NavBarHelper.setup(this, NavTab.RECORDS)

        scroll = findViewById(R.id.recordsScroll)
        stateView = findViewById(R.id.stateView)
        stateTitle = findViewById(R.id.stateTitle)
        stateBody = findViewById(R.id.stateBody)
        totalCaloriesValue = findViewById(R.id.totalCaloriesValue)
        totalCaloriesCaption = findViewById(R.id.totalCaloriesCaption)
        statTiles = findViewById(R.id.statTiles)
        bestsCard = findViewById(R.id.bestsCard)
        lifetimeCard = findViewById(R.id.lifetimeCard)
        topExercisesTitle = findViewById(R.id.topExercisesTitle)
        topExercisesCard = findViewById(R.id.topExercisesCard)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: UiState<RecordsSummary>) {
        when (state) {
            is UiState.Loading -> Unit
            is UiState.Empty -> showState(
                getString(R.string.records_empty_title),
                getString(R.string.records_empty_body)
            )
            is UiState.Error -> showState(
                getString(R.string.records_error_title),
                state.message
            )
            is UiState.Success -> {
                stateView.visibility = View.GONE
                scroll.visibility = View.VISIBLE
                bind(state.data)
            }
        }
    }

    private fun showState(title: String, body: String) {
        stateTitle.text = title
        stateBody.text = body
        stateView.visibility = View.VISIBLE
        scroll.visibility = View.GONE
    }

    private fun bind(data: RecordsSummary) {
        totalCaloriesValue.text = getString(R.string.records_kcal_value, num(data.totalCalories))
        totalCaloriesCaption.text = getString(
            R.string.records_total_caption,
            data.totalWorkouts,
            num(data.totalActiveMinutes),
            num(data.totalSteps)
        )

        statTiles.removeAllViews()
        addTile(num(data.currentStreak), getString(R.string.record_current_streak))
        addTile(num(data.longestStreak), getString(R.string.record_longest_streak))
        addTile(num(data.activeDays), getString(R.string.record_active_days))

        bestsCard.removeAllViews()
        data.bestStepDay?.let {
            addRecord(
                bestsCard, "👟", getString(R.string.record_best_step_day),
                it.date.format(dayFormat), getString(R.string.records_steps_value, num(it.value))
            )
        }
        data.bestCalorieDay?.let {
            addRecord(
                bestsCard, "🔥", getString(R.string.record_best_calorie_day),
                it.date.format(dayFormat), getString(R.string.records_kcal_value, num(it.value))
            )
        }
        data.bestStepWeek?.let {
            addRecord(
                bestsCard, "📈", getString(R.string.record_best_week),
                getString(
                    R.string.records_week_range,
                    it.start.format(shortDayFormat),
                    it.end.format(shortDayFormat)
                ),
                getString(R.string.records_steps_value, num(it.total))
            )
        }
        data.longestWorkout?.let {
            addRecord(
                bestsCard, it.icon ?: "⏱️", getString(R.string.record_longest_workout),
                getString(R.string.records_workout_caption, it.name, it.date.format(shortDayFormat)),
                getString(R.string.records_minutes_value, num(it.minutes))
            )
        }
        data.hardestWorkout?.let {
            addRecord(
                bestsCard, it.icon ?: "💥", getString(R.string.record_hardest_workout),
                getString(R.string.records_workout_caption, it.name, it.date.format(shortDayFormat)),
                getString(R.string.records_kcal_value, num(it.calories))
            )
        }
        bestsCard.visibility = if (bestsCard.childCount == 0) View.GONE else View.VISIBLE

        lifetimeCard.removeAllViews()
        addRecord(
            lifetimeCard, "⏳", getString(R.string.record_total_time),
            getString(R.string.records_across_workouts, data.totalWorkouts),
            formatDuration(data.totalActiveMinutes)
        )
        addRecord(
            lifetimeCard, "👣", getString(R.string.record_total_steps),
            getString(R.string.records_across_days, data.activeDays),
            num(data.totalSteps)
        )
        data.busiestWeekday?.let {
            addRecord(
                lifetimeCard, "📅", getString(R.string.record_busiest_day),
                getString(R.string.records_busiest_caption, it.workouts, it.dayName),
                it.dayName
            )
        }
        data.trainingSince?.let {
            addRecord(
                lifetimeCard, "🌱", getString(R.string.record_training_since),
                getString(R.string.records_days_ago, daysSince(it)),
                it.format(shortDayFormat)
            )
        }

        topExercisesCard.removeAllViews()
        data.topExercises.forEach { tally ->
            addRecord(
                topExercisesCard, tally.icon ?: "💪", tally.name,
                getString(R.string.records_tally_caption, tally.count, num(tally.minutes)),
                getString(R.string.records_kcal_value, num(tally.calories))
            )
        }
        val hasTop = data.topExercises.isNotEmpty()
        topExercisesCard.visibility = if (hasTop) View.VISIBLE else View.GONE
        topExercisesTitle.visibility = if (hasTop) View.VISIBLE else View.GONE
    }

    private fun addTile(value: String, label: String) {
        val tile = layoutInflater.inflate(R.layout.item_stat_tile, statTiles, false)
        tile.findViewById<TextView>(R.id.tileValue).text = value
        tile.findViewById<TextView>(R.id.tileLabel).text = label
        if (statTiles.childCount > 0) {
            (tile.layoutParams as LinearLayout.LayoutParams).marginStart =
                resources.getDimensionPixelSize(R.dimen.space_sm)
        }
        statTiles.addView(tile)
    }

    private fun addRecord(
        parent: LinearLayout,
        icon: String,
        label: String,
        caption: String,
        value: String
    ) {
        val row = layoutInflater.inflate(R.layout.item_record_row, parent, false)
        row.findViewById<TextView>(R.id.recordIcon).text = icon
        row.findViewById<TextView>(R.id.recordLabel).text = label
        row.findViewById<TextView>(R.id.recordCaption).text = caption
        row.findViewById<TextView>(R.id.recordValue).text = value
        parent.addView(row)
    }

    private fun num(value: Int): String = numbers.format(value)

    /** 95 minutes reads better as 1h 35m than as 95 min. */
    private fun formatDuration(minutes: Int): String =
        if (minutes < 60) {
            getString(R.string.records_minutes_value, num(minutes))
        } else {
            getString(R.string.records_hours_minutes, minutes / 60, minutes % 60)
        }

    private fun daysSince(date: LocalDate): Int =
        (java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now()).toInt() + 1)
            .coerceAtLeast(1)
}
