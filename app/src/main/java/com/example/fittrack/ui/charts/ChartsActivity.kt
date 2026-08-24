package com.example.fittrack.ui.charts

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
@AndroidEntryPoint
class ChartsActivity : AppCompatActivity() {

    private val viewModel: ChartsViewModel by viewModels()
    private var currentDays: List<com.example.fittrack.domain.model.DailySteps> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_charts)

        findViewById<TextView>(R.id.screenTitle).setText(R.string.charts_title)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        NavBarHelper.setup(this, NavTab.CHARTS)

        val barChart = findViewById<BarChart>(R.id.stepsBarChart)
        val weekLabel = findViewById<TextView>(R.id.weekLabel)
        val selectedDayText = findViewById<TextView>(R.id.selectedDayText)
        val bestDayStat = findViewById<TextView>(R.id.bestDayStat)
        val bestWeekStat = findViewById<TextView>(R.id.bestWeekStat)
        val bestMonthStat = findViewById<TextView>(R.id.bestMonthStat)
        val trendLineChart = findViewById<LineChart>(R.id.trendLineChart)
        val streakValue = findViewById<TextView>(R.id.streakValue)
        trendLineChart.description.isEnabled = false
        trendLineChart.legend.isEnabled = false
        trendLineChart.axisRight.isEnabled = false
        styleChartForDarkTheme(trendLineChart)

        findViewById<View>(R.id.prevWeekBtn).setOnClickListener { viewModel.goToPreviousWeek() }
        findViewById<View>(R.id.nextWeekBtn).setOnClickListener { viewModel.goToNextWeek() }

        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.axisRight.isEnabled = false
        barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        barChart.xAxis.setDrawGridLines(false)
        styleChartForDarkTheme(barChart)

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
                                color = ContextCompat.getColor(this@ChartsActivity, R.color.brand)
                                setDrawValues(false)
                                highLightColor = ContextCompat.getColor(this@ChartsActivity, R.color.brand_bright)
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
                            barChart.animateY(600)

                            val trendEntries = summary.trendWeeks.mapIndexed { index, (_, total) ->
                                Entry(index.toFloat(), total.toFloat())
                            }
                            val trendLabels = summary.trendWeeks.map { it.first }
                            val trendDataSet = LineDataSet(trendEntries, "Weekly Total").apply {
                                color = ContextCompat.getColor(this@ChartsActivity, R.color.brand_bright)
                                setCircleColor(ContextCompat.getColor(this@ChartsActivity, R.color.brand_bright))
                                setCircleHoleColor(ContextCompat.getColor(this@ChartsActivity, R.color.surface))
                                lineWidth = 2.5f
                                circleRadius = 4f
                                setDrawValues(false)
                                mode = LineDataSet.Mode.CUBIC_BEZIER
                            }
                            trendLineChart.data = LineData(trendDataSet)
                            trendLineChart.xAxis.valueFormatter = IndexAxisValueFormatter(trendLabels)
                            trendLineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
                            trendLineChart.animateX(600)
                            trendLineChart.invalidate()

                            streakValue.text = "${summary.currentStreak} days"
                        }
                        is UiState.Error -> weekLabel.text = "Error loading"
                        else -> Unit
                    }
                }
            }
        }
    }

    /**
     * MPAndroidChart defaults to black labels and grid lines, which are invisible
     * on the dark surfaces this app uses, so every chart gets the same treatment.
     */
    private fun styleChartForDarkTheme(chart: com.github.mikephil.charting.charts.BarLineChartBase<*>) {
        val label = ContextCompat.getColor(this, R.color.text_secondary)
        val grid = ContextCompat.getColor(this, R.color.chart_grid)

        chart.setNoDataTextColor(label)
        chart.setDrawGridBackground(false)
        chart.setDrawBorders(false)
        chart.setExtraOffsets(4f, 8f, 4f, 4f)

        chart.xAxis.apply {
            textColor = label
            axisLineColor = grid
            gridColor = grid
            textSize = 11f
        }
        chart.axisLeft.apply {
            textColor = label
            axisLineColor = grid
            gridColor = grid
            textSize = 11f
            setDrawAxisLine(false)
            // Anchor at zero so bar heights stay proportional to the values.
            axisMinimum = 0f
        }
    }
}
