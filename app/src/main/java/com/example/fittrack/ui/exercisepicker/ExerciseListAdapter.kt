package com.example.fittrack.ui.exercisepicker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fittrack.R
import com.example.fittrack.domain.model.Exercise

/**
 * Flat list of section headers and exercises. The sectioning itself is decided
 * in the ViewModel; this only renders whatever rows it is handed.
 */
class ExerciseListAdapter(
    private val onSelect: (Exercise) -> Unit,
    private val onToggleFavorite: (Exercise) -> Unit,
    private val onEditCustom: (Exercise) -> Unit
) : ListAdapter<PickerRow, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is PickerRow.Header -> TYPE_HEADER
        is PickerRow.Item -> TYPE_EXERCISE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_exercise_header, parent, false))
        } else {
            ExerciseViewHolder(inflater.inflate(R.layout.item_exercise, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is PickerRow.Header -> (holder as HeaderViewHolder).bind(row)
            is PickerRow.Item -> (holder as ExerciseViewHolder).bind(row)
        }
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title = view as TextView
        fun bind(row: PickerRow.Header) {
            title.text = row.title
        }
    }

    private inner class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: TextView = view.findViewById(R.id.exerciseIcon)
        private val name: TextView = view.findViewById(R.id.exerciseName)
        private val meta: TextView = view.findViewById(R.id.exerciseMeta)
        private val favorite: ImageView = view.findViewById(R.id.favoriteBtn)
        private val edit: ImageView = view.findViewById(R.id.editCustomBtn)

        fun bind(row: PickerRow.Item) {
            val exercise = row.exercise
            icon.text = exercise.icon
            name.text = exercise.name
            meta.text = itemView.context.getString(
                R.string.exercise_meta,
                exercise.category.displayName,
                formatMet(exercise.met),
                row.kcalPerHour
            )

            favorite.setImageResource(
                if (exercise.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            favorite.setOnClickListener { onToggleFavorite(exercise) }

            edit.visibility = if (exercise.isCustom) View.VISIBLE else View.GONE
            edit.setOnClickListener { onEditCustom(exercise) }

            itemView.setOnClickListener { onSelect(exercise) }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_EXERCISE = 1

        /** 9.8 stays "9.8", 6.0 becomes "6" so the caption does not read oddly. */
        fun formatMet(met: Double): String =
            if (met % 1.0 == 0.0) met.toInt().toString() else met.toString()

        private val DIFF = object : DiffUtil.ItemCallback<PickerRow>() {
            override fun areItemsTheSame(oldItem: PickerRow, newItem: PickerRow): Boolean =
                when {
                    oldItem is PickerRow.Header && newItem is PickerRow.Header ->
                        oldItem.title == newItem.title
                    oldItem is PickerRow.Item && newItem is PickerRow.Item ->
                        oldItem.exercise.id == newItem.exercise.id
                    else -> false
                }

            override fun areContentsTheSame(oldItem: PickerRow, newItem: PickerRow): Boolean =
                oldItem == newItem
        }
    }
}
