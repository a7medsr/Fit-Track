package com.example.fittrack.ui.charts

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fittrack.R
import com.example.fittrack.ui.common.UiState
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChartsActivity : AppCompatActivity() {

    private val viewModel: ChartsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_charts)

        val barChart = findViewById<BarChart>(R.id.stepsBarChart)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            val entries = state.data.mapIndexed { index, daily ->
                                BarEntry(index.toFloat(), daily.stepCount.toFloat())
                            }
                            val dataSet = BarDataSet(entries, "Steps")
                            barChart.data = BarData(dataSet)
                            barChart.invalidate()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}