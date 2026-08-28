package com.example.fittrack.ui.logworkout

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fittrack.R
import com.example.fittrack.data.prefs.UserPreferences
import com.example.fittrack.domain.model.Routine
import com.example.fittrack.domain.util.CalorieCalculator
import com.example.fittrack.ui.common.NavBarHelper
import com.example.fittrack.ui.common.NavTab
import com.example.fittrack.ui.common.UiState
import com.example.fittrack.ui.exercisepicker.ExerciseListAdapter
import com.example.fittrack.ui.exercisepicker.ExercisePickerActivity
import com.example.fittrack.ui.routine.RoutineEditorActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

import com.example.fittrack.ui.history.HistoryActivity

@AndroidEntryPoint
class LogWorkoutActivity : AppCompatActivity() {

    private val viewModel: LogWorkoutViewModel by viewModels()

    private lateinit var notesInput: EditText
    private lateinit var todayTotalCalories: TextView
    private lateinit var todayTotalSummary: TextView
    private lateinit var weightValue: TextView
    private lateinit var selectedIcon: TextView
    private lateinit var selectedName: TextView
    private lateinit var selectedMeta: TextView
    private lateinit var selectedLoggedToday: TextView
    private lateinit var logControls: View
    private lateinit var durationValue: TextView
    private lateinit var estimatedCalories: TextView
    private lateinit var sessionsContainer: LinearLayout
    private lateinit var sessionsEmptyHint: View
    private lateinit var autoWalkSteps: TextView
    private lateinit var autoWalkCalories: TextView

    private val pickExercise = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val id = result.data?.getLongExtra(ExercisePickerActivity.EXTRA_EXERCISE_ID, 0L) ?: 0L
        if (id > 0L) viewModel.selectExercise(id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_workout)

        findViewById<TextView>(R.id.screenTitle).setText(R.string.log_title)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        NavBarHelper.setup(this, NavTab.LOG)

        notesInput = findViewById(R.id.notesInput)
        todayTotalCalories = findViewById(R.id.todayTotalCalories)
        todayTotalSummary = findViewById(R.id.todayTotalSummary)
        weightValue = findViewById(R.id.weightValue)
        selectedIcon = findViewById(R.id.selectedIcon)
        selectedName = findViewById(R.id.selectedName)
        selectedMeta = findViewById(R.id.selectedMeta)
        selectedLoggedToday = findViewById(R.id.selectedLoggedToday)
        logControls = findViewById(R.id.logControls)
        sessionsContainer = findViewById(R.id.sessionsContainer)
        sessionsEmptyHint = findViewById(R.id.sessionsEmptyHint)
        durationValue = findViewById(R.id.durationValue)
        estimatedCalories = findViewById(R.id.estimatedCalories)

        // Walking is auto-tracked from the step counter, so it keeps its own
        // read-only card rather than becoming something you can log by hand.
        val walkingContainer = findViewById<LinearLayout>(R.id.walkingCardContainer)
        val autoWalkCard = layoutInflater.inflate(R.layout.item_auto_walking, walkingContainer, false)
        walkingContainer.addView(autoWalkCard)
        autoWalkSteps = autoWalkCard.findViewById(R.id.autoWalkSteps)
        autoWalkCalories = autoWalkCard.findViewById(R.id.autoWalkCalories)

        findViewById<View>(R.id.viewHistoryBtn).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<View>(R.id.chooseExerciseRow).setOnClickListener {
            pickExercise.launch(Intent(this, ExercisePickerActivity::class.java))
        }
        findViewById<View>(R.id.weightRow).setOnClickListener { openWeightDialog() }
        findViewById<View>(R.id.newSessionBtn).setOnClickListener {
            startActivity(RoutineEditorActivity.newIntent(this))
        }

        findViewById<TextView>(R.id.durationPlus).setOnClickListener {
            viewModel.changeDuration(LogWorkoutViewModel.DURATION_STEP_MINUTES)
        }
        findViewById<TextView>(R.id.durationMinus).setOnClickListener {
            viewModel.changeDuration(-LogWorkoutViewModel.DURATION_STEP_MINUTES)
        }
        findViewById<TextView>(R.id.submitActivityBtn).setOnClickListener {
            val name = selectedName.text.toString()
            viewModel.submitActivity(notesInput.text.toString())
            Toast.makeText(this, getString(R.string.activity_logged, name), Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Success -> render(state.data)
                        is UiState.Empty -> render(null)
                        is UiState.Error -> {
                            render(null)
                            todayTotalSummary.text = state.message
                        }
                        is UiState.Loading -> Unit
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The library can rename or delete the exercise this screen is holding.
        viewModel.refreshSelection()
    }

    /** [state] is null for Loading/Empty/Error, where the screen shows zeroes. */
    private fun render(state: LogScreenState?) {
        val weight = state?.weightKg ?: UserPreferences.DEFAULT_WEIGHT_KG
        weightValue.text = getString(R.string.weight_value, weight)

        val steps = state?.todaySteps ?: 0
        val walkingCalories = state?.walkingCalories ?: 0
        autoWalkSteps.text = getString(R.string.steps_today, steps)
        autoWalkCalories.text = getString(R.string.calories_estimate, walkingCalories)

        todayTotalCalories.text = getString(R.string.calories_value, state?.totalCalories ?: 0)
        val workouts = state?.todayWorkouts.orEmpty()
        todayTotalSummary.text = if (workouts.isEmpty()) {
            getString(R.string.summary_walking_only, walkingCalories)
        } else {
            workouts.groupBy { it.type }
                .map { (type, list) -> "$type ×${list.size}" }
                .joinToString(" · ") + " · " + getString(R.string.walking)
        }

        renderSessions(state)

        val exercise = state?.selectedExercise
        if (exercise == null) {
            selectedIcon.text = getString(R.string.exercise_placeholder_icon)
            selectedName.setText(R.string.choose_exercise)
            selectedMeta.setText(R.string.choose_exercise_hint)
            selectedLoggedToday.visibility = View.GONE
            logControls.visibility = View.GONE
            return
        }

        selectedIcon.text = exercise.icon
        selectedName.text = exercise.name
        selectedMeta.text = getString(
            R.string.exercise_meta,
            exercise.category.displayName,
            ExerciseListAdapter.formatMet(exercise.met),
            CalorieCalculator.kcalPerHour(exercise.met, weight)
        )

        val loggedToday = state.selectedLoggedToday
        selectedLoggedToday.visibility = if (loggedToday > 0) View.VISIBLE else View.GONE
        selectedLoggedToday.text = getString(R.string.logged_today_count, loggedToday)

        logControls.visibility = View.VISIBLE
        durationValue.text = getString(R.string.duration_value, state.durationMinutes)
        estimatedCalories.text = getString(R.string.calories_estimate, state.estimatedCalories)
    }

    private fun renderSessions(state: LogScreenState?) {
        val routines = state?.routines.orEmpty()
        sessionsEmptyHint.visibility = if (routines.isEmpty()) View.VISIBLE else View.GONE
        sessionsContainer.removeAllViews()
        if (state == null) return
        routines.forEach { routine ->
            sessionsContainer.addView(buildSessionCard(state, routine))
        }
    }

    private fun buildSessionCard(state: LogScreenState, routine: Routine): View {
        val card = layoutInflater.inflate(R.layout.item_routine_card, sessionsContainer, false)

        card.findViewById<TextView>(R.id.routineIcon).text = routine.icon
        card.findViewById<TextView>(R.id.routineName).text = routine.name
        card.findViewById<TextView>(R.id.routineMeta).text = getString(
            R.string.routine_meta,
            resources.getQuantityString(
                R.plurals.exercise_count, routine.exerciseCount, routine.exerciseCount
            ),
            routine.totalMinutes,
            state.caloriesForRoutine(routine)
        )

        val loggedPill = card.findViewById<TextView>(R.id.routineLoggedToday)
        val alreadyLogged = state.isRoutineLoggedToday(routine)
        loggedPill.visibility = if (alreadyLogged) View.VISIBLE else View.GONE
        loggedPill.setText(R.string.routine_logged_today)

        card.findViewById<View>(R.id.routineBody).setOnClickListener {
            startActivity(RoutineEditorActivity.newIntent(this, routine.id))
        }

        val play = card.findViewById<TextView>(R.id.routinePlayBtn)
        // An empty session would log nothing, so make that visibly unavailable
        // rather than silently doing nothing on tap.
        play.isEnabled = routine.items.isNotEmpty()
        play.alpha = if (routine.items.isNotEmpty()) 1f else 0.4f
        play.contentDescription = getString(R.string.cd_log_session)
        play.setOnClickListener {
            viewModel.logRoutine(routine, notesInput.text.toString())
            Toast.makeText(
                this,
                getString(
                    R.string.routine_logged,
                    routine.name,
                    resources.getQuantityString(
                        R.plurals.exercise_count, routine.exerciseCount, routine.exerciseCount
                    )
                ),
                Toast.LENGTH_SHORT
            ).show()
        }

        return card
    }

    private fun openWeightDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_set_weight, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val input = view.findViewById<EditText>(R.id.weightInput)
        val current = (viewModel.uiState.value as? UiState.Success)?.data?.weightKg
            ?: UserPreferences.DEFAULT_WEIGHT_KG
        input.setText(current.toString())

        fun nudge(delta: Int) {
            val value = (input.text.toString().toIntOrNull() ?: current) + delta
            input.setText(value.coerceIn(UserPreferences.MIN_WEIGHT_KG, UserPreferences.MAX_WEIGHT_KG).toString())
        }
        view.findViewById<TextView>(R.id.weightPlus).setOnClickListener { nudge(1) }
        view.findViewById<TextView>(R.id.weightMinus).setOnClickListener { nudge(-1) }

        view.findViewById<TextView>(R.id.cancelBtn).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.saveBtn).setOnClickListener {
            val value = input.text.toString().toIntOrNull()
            if (value != null && value > 0) {
                viewModel.updateWeight(value)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
