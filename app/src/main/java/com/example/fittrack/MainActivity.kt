package com.example.fittrack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fittrack.ui.charts.ChartsActivity
import com.example.fittrack.ui.common.UiState
import com.example.fittrack.ui.dashboard.DashboardViewModel
import com.example.fittrack.ui.auth.SignInActivity
import com.example.fittrack.ui.logworkout.LogWorkoutActivity
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
        val stepGoalLabel = findViewById<TextView>(R.id.stepGoalLabel)

        val openGoalDialog = View.OnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_set_goal, null)
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val customInput = dialogView.findViewById<android.widget.EditText>(R.id.customGoalInput)

            val presets = mapOf(
                R.id.preset5k to 5000,
                R.id.preset8k to 8000,
                R.id.preset10k to 10000,
                R.id.preset15k to 15000
            )
            presets.forEach { (id, value) ->
                dialogView.findViewById<TextView>(id).setOnClickListener {
                    viewModel.updateGoal(value)
                    dialog.dismiss()
                }
            }

            dialogView.findViewById<TextView>(R.id.saveBtn).setOnClickListener {
                val newGoal = customInput.text.toString().toIntOrNull()
                if (newGoal != null && newGoal > 0) {
                    viewModel.updateGoal(newGoal)
                    dialog.dismiss()
                }
            }

            dialogView.findViewById<TextView>(R.id.cancelBtn).setOnClickListener { dialog.dismiss() }

            dialog.show()
        }

        // Reachable from the goal label itself and from the labelled button
        // below the ring, which is the discoverable entry point.
        stepGoalLabel.setOnClickListener(openGoalDialog)
        findViewById<View>(R.id.editGoalBtn).setOnClickListener(openGoalDialog)

        findViewById<View>(R.id.quickLogBtn).setOnClickListener {
            startActivity(Intent(this, LogWorkoutActivity::class.java))
        }
        findViewById<View>(R.id.quickChartsBtn).setOnClickListener {
            startActivity(Intent(this, ChartsActivity::class.java))
        }

        // Account row: who is signed in, and the way back out.
        val accountLabel = findViewById<TextView>(R.id.accountLabel)
        val accountAction = findViewById<TextView>(R.id.signOutBtn)
        val signedInUser = viewModel.currentUser

        if (signedInUser == null) {
            accountLabel.setText(R.string.auth_signed_out)
            accountAction.setText(R.string.auth_sign_in_short)
            accountAction.setOnClickListener { goToSignIn() }
        } else {
            accountLabel.text = getString(
                R.string.auth_signed_in_as,
                signedInUser.displayName ?: signedInUser.email ?: "your account"
            )
            accountAction.setText(R.string.auth_sign_out)
            accountAction.setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage(R.string.auth_sign_out_confirm)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.auth_sign_out) { _, _ ->
                        viewModel.signOut()
                        goToSignIn()
                    }
                    .show()
            }
        }

        com.example.fittrack.ui.common.NavBarHelper.setup(this, com.example.fittrack.ui.common.NavTab.HOME)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            stepCountBig.text = "${state.data.stepCount}"
                            stepGoalLabel.text = "/ ${state.data.goal} steps"
                            stepRing.progress = state.data.stepCount / state.data.goal.toFloat()
                        }
                        is UiState.Error -> stepCountBig.text = "Error"
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun goToSignIn() {
        startActivity(
            Intent(this, SignInActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finishAffinity()
    }
}
