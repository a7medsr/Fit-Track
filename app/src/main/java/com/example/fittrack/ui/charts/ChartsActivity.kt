package com.example.fittrack.ui.charts

import android.os.Bundle
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
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChartsActivity : AppCompatActivity() {

    private val viewModel: ChartsViewModel by viewModels()
    private var currentDays: List<com.example.fittrack.domain.model.DailySteps> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_charts)

        findViewById<TextView>(R.id.backButton).setOnClickListener { finish() }
        NavBarHelper.setup(this, NavTab.CHARTS)

        val barChart = findViewById<BarChart>(R.id.stepsBarChart)
        val weekLabel = findViewById<TextView>(R.id.weekLabel)
        val selectedDayText = findViewById<TextView>(R.id.selectedDayText)
        val bestDayStat = findViewById<TextView>(R.id.bestDayStat)
        val bestWeekStat = findViewById<TextView>(R.id.bestWeekStat)
        val bestMonthStat = findViewById<TextView>(R.id.bestMonthStat)

        findViewById<TextView>(R.id.prevWeekBtn).setOnClickListener { viewModel.goToPreviousWeek() }
        findViewById<TextView>(R.id.nextWeekBtn).setOnClickListener { viewModel.goToNextWeek() }

        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.axisRight.isEnabled = false
        barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        barChart.xAxis.setDrawGridLines(false)

        barChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val index = e?.x?.toInt() ?: return
                val day = currentDays.getOrNull(index) ?: return
                selectedDayText.text = "${day.date}: ${day.stepCount} steps"
            }
            override fun onNothingSelected() {
                selectedDayText.text = "Tap a bar to see that day's steps"
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            val summary = state.data
                            currentDays = summary.days

                            val entries = summary.days.mapIndexed { index, daily ->
                                BarEntry(index.toFloat(), daily.stepCount.toFloat())
                            }
                            val dayLabels = summary.days.map { it.date.takeLast(2) }

                            val dataSet = BarDataSet(entries, "Steps").apply {
                                color = resources.getColor(R.color.accent_green, theme)
                                setDrawValues(false)
                                highLightColor = resources.getColor(R.color.accent_blue, theme)
                                highLightAlpha = 255
                            }
                            barChart.data = BarData(dataSet).apply { barWidth = 0.6f }
                            barChart.xAxis.valueFormatter = IndexAxisValueFormatter(dayLabels)
                            barChart.highlightValue(null)
                            barChart.invalidate()

                            weekLabel.text = summary.weekLabel
                            selectedDayText.text = "Tap a bar to see that day's steps"
                            bestDayStat.text = "${summary.bestDaySteps}"
                            bestWeekStat.text = "${summary.bestWeekTotal}"
                            bestMonthStat.text = "${summary.bestMonthTotal}"
                        }
                        is UiState.Error -> weekLabel.text = "Error loading"
                        else -> Unit
                    }
                }
            }
        }
    }
}