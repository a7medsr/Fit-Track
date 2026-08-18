package com.example.fittrack.ui.records

import android.os.Bundle
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fittrack.R
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecordsActivity : AppCompatActivity() {

    private val viewModel: RecordsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)

        val bestDayValue = findViewById<TextView>(R.id.bestDayValue)
        val bestWeekValue = findViewById<TextView>(R.id.bestWeekValue)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            bestDayValue.text = "${state.data.bestDaySteps} steps (${state.data.bestDayDate})"
                            bestWeekValue.text = "${state.data.bestWeekTotal} steps"
                        }
                        is UiState.Error -> bestDayValue.text = "Error loading records"
                        else -> Unit
                    }
                }
            }
        }
    }
}