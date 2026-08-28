package com.example.fittrack.ui.exercisepicker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fittrack.R
import com.example.fittrack.domain.model.Exercise
import com.example.fittrack.domain.model.ExerciseCategory
import com.example.fittrack.ui.common.NavBarHelper
import com.example.fittrack.ui.common.NavTab
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Browse the exercise library and hand one back to the Log screen. Started for
 * a result, so the picked exercise comes back as [EXTRA_EXERCISE_ID].
 */
@AndroidEntryPoint
class ExercisePickerActivity : AppCompatActivity() {

    private val viewModel: ExercisePickerViewModel by viewModels()

    private lateinit var adapter: ExerciseListAdapter
    private lateinit var list: RecyclerView
    private lateinit var stateView: View
    private lateinit var stateTitle: TextView
    private lateinit var stateBody: TextView

    /** Parallel to the tab row: index 0 is "All", then one per category. */
    private val tabCategories: List<ExerciseCategory?> =
        listOf(null) + ExerciseCategory.entries
    private val tabViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_picker)

        findViewById<TextView>(R.id.screenTitle).setText(R.string.exercise_picker_title)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        NavBarHelper.setup(this, NavTab.LOG)

        list = findViewById(R.id.exerciseList)
        stateView = findViewById(R.id.stateView)
        stateTitle = findViewById(R.id.stateTitle)
        stateBody = findViewById(R.id.stateBody)

        adapter = ExerciseListAdapter(
            onSelect = ::selectExercise,
            onToggleFavorite = viewModel::toggleFavorite,
            onEditCustom = ::openEditDialog
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        val searchInput = findViewById<EditText>(R.id.searchInput)
        searchInput.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }

        findViewById<View>(R.id.addCustomBtn).setOnClickListener { openCreateDialog() }

        buildCategoryTabs()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.category.collect(::paintTabs) }
            }
        }
    }

    private fun render(state: UiState<List<PickerRow>>) {
        when (state) {
            is UiState.Loading -> showState(R.string.exercise_loading, R.string.exercise_empty_body, bodyVisible = false)
            is UiState.Empty -> showState(R.string.exercise_empty_title, R.string.exercise_empty_body)
            is UiState.Error -> {
                stateTitle.setText(R.string.exercise_error_title)
                stateBody.text = state.message
                stateBody.visibility = View.VISIBLE
                stateView.visibility = View.VISIBLE
                list.visibility = View.GONE
            }
            is UiState.Success -> {
                stateView.visibility = View.GONE
                list.visibility = View.VISIBLE
                adapter.submitList(state.data)
            }
        }
    }

    private fun showState(titleRes: Int, bodyRes: Int, bodyVisible: Boolean = true) {
        stateTitle.setText(titleRes)
        stateBody.setText(bodyRes)
        stateBody.visibility = if (bodyVisible) View.VISIBLE else View.GONE
        stateView.visibility = View.VISIBLE
        list.visibility = View.GONE
    }

    private fun buildCategoryTabs() {
        val row = findViewById<LinearLayout>(R.id.categoryTabs)
        tabCategories.forEachIndexed { index, category ->
            val chip = LayoutInflater.from(this)
                .inflate(R.layout.item_category_chip, row, false) as TextView
            chip.text = category?.displayName ?: getString(R.string.exercise_category_all)
            if (index > 0) {
                (chip.layoutParams as LinearLayout.LayoutParams).marginStart =
                    resources.getDimensionPixelSize(R.dimen.space_sm)
            }
            chip.setOnClickListener { viewModel.setCategory(category) }
            row.addView(chip)
            tabViews += chip
        }
    }

    private fun paintTabs(selected: ExerciseCategory?) {
        tabViews.forEachIndexed { index, chip ->
            val isSelected = tabCategories[index] == selected
            chip.setBackgroundResource(
                if (isSelected) R.drawable.bg_chip_category_selected else R.drawable.bg_chip_category
            )
            chip.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (isSelected) R.color.on_brand else R.color.text_secondary
                )
            )
        }
    }

    private fun selectExercise(exercise: Exercise) {
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(EXTRA_EXERCISE_ID, exercise.id)
        )
        finish()
    }

    private fun openCreateDialog() {
        CustomExerciseDialog.show(
            activity = this,
            existing = null,
            onSave = { name, category, icon, intensity ->
                viewModel.createCustomExercise(name, category, icon, intensity)
            },
            onDelete = { }
        )
    }

    private fun openEditDialog(exercise: Exercise) {
        CustomExerciseDialog.show(
            activity = this,
            existing = exercise,
            onSave = { name, category, icon, intensity ->
                viewModel.updateCustomExercise(exercise.id, name, category, icon, intensity)
            },
            onDelete = viewModel::deleteCustomExercise
        )
    }

    companion object {
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
    }
}
