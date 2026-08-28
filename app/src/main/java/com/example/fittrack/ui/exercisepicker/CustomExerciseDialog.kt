package com.example.fittrack.ui.exercisepicker

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.fittrack.R
import com.example.fittrack.domain.model.Exercise
import com.example.fittrack.domain.model.ExerciseCategory
import com.example.fittrack.domain.model.ExerciseIntensity

/**
 * Create-or-edit sheet for custom exercises, built the same way as the daily
 * goal dialog on the dashboard: an inflated card on a transparent window.
 *
 * Passing [existing] switches it to edit mode, which renames the title, fills
 * the fields in and reveals the delete button.
 */
object CustomExerciseDialog {

    fun show(
        activity: Activity,
        existing: Exercise?,
        onSave: (name: String, category: ExerciseCategory, icon: String, intensity: ExerciseIntensity) -> Unit,
        onDelete: (Exercise) -> Unit
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_custom_exercise, null)
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val nameInput = view.findViewById<EditText>(R.id.exerciseNameInput)
        val iconInput = view.findViewById<EditText>(R.id.exerciseIconInput)
        val categoryRow = view.findViewById<LinearLayout>(R.id.categoryRow)
        val intensityRow = view.findViewById<LinearLayout>(R.id.intensityRow)

        var selectedCategory = existing?.category ?: ExerciseCategory.CARDIO
        var selectedIntensity = existing?.intensity ?: ExerciseIntensity.MODERATE

        if (existing != null) {
            view.findViewById<TextView>(R.id.dialogTitle).setText(R.string.custom_exercise_title_edit)
            nameInput.setText(existing.name)
            iconInput.setText(existing.icon)
        }

        val categoryChips = ExerciseCategory.entries.map { category ->
            addChip(activity, categoryRow, category.displayName, categoryRow.childCount > 0)
        }
        val intensityChips = ExerciseIntensity.entries.map { intensity ->
            addChip(activity, intensityRow, intensity.displayName, intensityRow.childCount > 0)
        }

        fun paintCategories() {
            categoryChips.forEachIndexed { index, chip ->
                paintChip(activity, chip, ExerciseCategory.entries[index] == selectedCategory)
            }
        }
        fun paintIntensities() {
            intensityChips.forEachIndexed { index, chip ->
                paintChip(activity, chip, ExerciseIntensity.entries[index] == selectedIntensity)
            }
        }

        categoryChips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                selectedCategory = ExerciseCategory.entries[index]
                paintCategories()
            }
        }
        intensityChips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                selectedIntensity = ExerciseIntensity.entries[index]
                paintIntensities()
            }
        }
        paintCategories()
        paintIntensities()

        val deleteBtn = view.findViewById<TextView>(R.id.deleteExerciseBtn)
        if (existing != null) {
            deleteBtn.visibility = View.VISIBLE
            deleteBtn.setOnClickListener {
                onDelete(existing)
                dialog.dismiss()
            }
        }

        view.findViewById<TextView>(R.id.cancelBtn).setOnClickListener { dialog.dismiss() }

        view.findViewById<TextView>(R.id.saveBtn).setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(activity, R.string.custom_exercise_name_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val icon = iconInput.text.toString().trim()
                .ifEmpty { activity.getString(R.string.custom_exercise_default_icon) }
            onSave(name, selectedCategory, icon, selectedIntensity)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addChip(
        activity: Activity,
        row: LinearLayout,
        label: String,
        withSpacing: Boolean
    ): TextView {
        val chip = LayoutInflater.from(activity)
            .inflate(R.layout.item_choice_chip, row, false) as TextView
        chip.text = label
        if (withSpacing) {
            (chip.layoutParams as LinearLayout.LayoutParams).marginStart =
                activity.resources.getDimensionPixelSize(R.dimen.space_sm)
        }
        row.addView(chip)
        return chip
    }

    private fun paintChip(activity: Activity, chip: TextView, selected: Boolean) {
        chip.setBackgroundResource(
            if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip
        )
        chip.setTextColor(
            ContextCompat.getColor(
                activity,
                if (selected) R.color.on_brand else R.color.text_secondary
            )
        )
    }
}
