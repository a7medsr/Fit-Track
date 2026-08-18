package com.example.fittrack

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fittrack.ui.common.UiState
import com.example.fittrack.ui.dashboard.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val stepRing = findViewById<com.example.fittrack.ui.dashboard.StepProgressRingView>(R.id.stepRing)
        val stepCountBig = findViewById<TextView>(R.id.stepCountBig)

        com.example.fittrack.ui.common.NavBarHelper.setup(this, com.example.fittrack.ui.common.NavTab.HOME)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            stepCountBig.text = "${state.data.stepCount}"
                            stepRing.progress = state.data.stepCount / 10000f
                        }
                        is UiState.Error -> stepCountBig.text = "Error"
                        else -> Unit
                    }
                }
            }
        }
    }
}