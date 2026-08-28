package com.example.fittrack.ui.routine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fittrack.R
import com.example.fittrack.domain.model.RoutineItem
import com.example.fittrack.ui.common.NavBarHelper
import com.example.fittrack.ui.common.NavTab
import com.example.fittrack.ui.common.UiState
import com.example.fittrack.ui.exercisepicker.ExerciseListAdapter
import com.example.fittrack.ui.exercisepicker.ExercisePickerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Build or edit a saved gym session: name it, add exercises from the library,
 * set how long each one runs. The Log screen then replays the whole thing.
 */
@AndroidEntryPoint
class RoutineEditorActivity : AppCompatActivity() {

    private val viewModel: RoutineEditorViewModel by viewModels()

    private lateinit var nameInput: EditText
    private lateinit var iconInput: EditText
    private lateinit var itemsContainer: LinearLayout
    private lateinit var emptyHint: View
    private lateinit var summaryCard: View
    private lateinit var summaryExercises: TextView
    private lateinit var summaryMinutes: TextView
    private lateinit var summaryCalories: TextView

    private val addExercise = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val id = result.data?.getLongExtra(ExercisePickerActivity.EXTRA_EXERCISE_ID, 0L) ?: 0L
        if (id > 0L) viewModel.addExercise(id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routine_editor)

        val routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, 0L)

        findViewById<TextView>(R.id.screenTitle).setText(
            if (routineId > 0L) R.string.routine_title_edit else R.string.routine_title_new
        )
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        NavBarHelper.setup(this, NavTab.LOG)

        nameInput = findViewById(R.id.routineNameInput)
        iconInput = findViewById(R.id.routineIconInput)
        itemsContainer = findViewById(R.id.routineItemsContainer)
        emptyHint = findViewById(R.id.routineEmptyHint)
        summaryCard = findViewById(R.id.summaryCard)
        summaryExercises = findViewById(R.id.summaryExercises)
        summaryMinutes = findViewById(R.id.summaryMinutes)
        summaryCalories = findViewById(R.id.summaryCalories)

        nameInput.doAfterTextChanged { viewModel.setName(it?.toString().orEmpty()) }
        iconInput.doAfterTextChanged { viewModel.setIcon(it?.toString().orEmpty()) }

        findViewById<View>(R.id.addExerciseBtn).setOnClickListener {
            addExercise.launch(Intent(this, ExercisePickerActivity::class.java))
        }

        findViewById<View>(R.id.saveRoutineBtn).setOnClickListener {
            if (!viewModel.save(nameInput.text.toString())) {
                Toast.makeText(this, R.string.routine_incomplete, Toast.LENGTH_SHORT).show()
            }
        }

        val deleteBtn = findViewById<View>(R.id.deleteRoutineBtn)
        deleteBtn.setOnClickListener { viewModel.delete() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        deleteBtn.visibility = if (viewModel.isEditing) View.VISIBLE else View.GONE
                        render(state)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is RoutineEditorEvent.Saved -> finish()
                            is RoutineEditorEvent.Loaded -> {
                                nameInput.setText(event.name)
                                iconInput.setText(event.icon)
                            }
                        }
                    }
                }
            }
        }

        viewModel.load(routineId)
    }

    private fun render(state: UiState<RoutineDraft>) {
        when (state) {
            is UiState.Loading -> Unit
            is UiState.Empty -> {
                itemsContainer.removeAllViews()
                emptyHint.visibility = View.VISIBLE
                summaryCard.visibility = View.GONE
            }
            is UiState.Error -> {
                itemsContainer.removeAllViews()
                emptyHint.visibility = View.VISIBLE
                summaryCard.visibility = View.GONE
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
            is UiState.Success -> {
                emptyHint.visibility = View.GONE
                summaryCard.visibility = View.VISIBLE
                renderItems(state.data)
            }
        }
    }

    private fun renderItems(draft: RoutineDraft) {
        summaryExercises.text = resources.getQuantityString(
            R.plurals.exercise_count, draft.items.size, draft.items.size
        )
        summaryMinutes.text = getString(R.string.duration_value, draft.totalMinutes)
        summaryCalories.text = getString(R.string.calories_estimate, draft.totalCalories)

        itemsContainer.removeAllViews()
        draft.items.forEachIndexed { index, item ->
            itemsContainer.addView(buildItemRow(draft, item, index))
        }
    }

    private fun buildItemRow(draft: RoutineDraft, item: RoutineItem, index: Int): View {
        val row = layoutInflater.inflate(R.layout.item_routine_exercise, itemsContainer, false)

        row.findViewById<TextView>(R.id.itemIcon).text = item.exercise.icon
        row.findViewById<TextView>(R.id.itemName).text = item.exercise.name
        row.findViewById<TextView>(R.id.itemMeta).text = getString(
            R.string.routine_item_meta,
            ExerciseListAdapter.formatMet(item.exercise.met),
            draft.caloriesFor(item)
        )
        row.findViewById<TextView>(R.id.itemDuration).text =
            getString(R.string.duration_value, item.durationMinutes)

        row.findViewById<View>(R.id.itemMinus).setOnClickListener {
            viewModel.changeDuration(index, -RoutineEditorViewModel.ITEM_STEP_MINUTES)
        }
        row.findViewById<View>(R.id.itemPlus).setOnClickListener {
            viewModel.changeDuration(index, RoutineEditorViewModel.ITEM_STEP_MINUTES)
        }
        row.findViewById<View>(R.id.itemRemove).setOnClickListener {
            viewModel.removeItem(index)
        }

        // The ends of the list can only move one way, so grey out the dead direction.
        val moveUp = row.findViewById<View>(R.id.itemMoveUp)
        val moveDown = row.findViewById<View>(R.id.itemMoveDown)
        moveUp.isEnabled = index > 0
        moveUp.alpha = if (index > 0) 1f else 0.3f
        moveDown.isEnabled = index < draft.items.lastIndex
        moveDown.alpha = if (index < draft.items.lastIndex) 1f else 0.3f
        moveUp.setOnClickListener { viewModel.moveItem(index, -1) }
        moveDown.setOnClickListener { viewModel.moveItem(index, 1) }

        return row
    }

    companion object {
        private const val EXTRA_ROUTINE_ID = "extra_routine_id"

        fun newIntent(context: Context, routineId: Long = 0L): Intent =
            Intent(context, RoutineEditorActivity::class.java)
                .putExtra(EXTRA_ROUTINE_ID, routineId)
    }
}
