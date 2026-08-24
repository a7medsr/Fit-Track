package com.example.fittrack.ui.records

import android.os.Bundle
import android.view.View
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
class RecordsActivity : AppCompatActivity() {

    private val viewModel: RecordsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)

        findViewById<TextView>(R.id.screenTitle).setText(R.string.records_title)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        NavBarHelper.setup(this, NavTab.RECORDS)

        val bestDayValue = findViewById<TextView>(R.id.bestDayValue)
        val bestDayDate = findViewById<TextView>(R.id.bestDayDate)
        val bestWeekValue = findViewById<TextView>(R.id.bestWeekValue)
        val bestWeekDate = findViewById<TextView>(R.id.bestWeekDate)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            bestDayValue.text = "${state.data.bestDaySteps} steps"
                            bestDayDate.text = state.data.bestDayDate
                            bestWeekValue.text = "${state.data.bestWeekTotal} steps"
                            bestWeekDate.text = state.data.bestWeekLabel
                        }
                        is UiState.Error -> bestDayValue.text = "Error loading"
                        else -> Unit
                    }
                }
            }
        }
    }
}