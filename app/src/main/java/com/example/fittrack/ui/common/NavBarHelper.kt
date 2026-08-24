package com.example.fittrack.ui.common

import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.fittrack.R
import com.example.fittrack.ui.charts.ChartsActivity
import com.example.fittrack.ui.records.RecordsActivity
import com.example.fittrack.ui.logworkout.LogWorkoutActivity
import com.example.fittrack.ui.history.HistoryActivity

enum class NavTab { HOME, HISTORY, LOG, CHARTS, RECORDS }

object NavBarHelper {

    /** A tab's container plus the icon and label it tints when selected. */
    private data class TabViews(
        val container: View,
        val icon: ImageView,
        val label: TextView,
        /** The Log tab sits on a filled green badge, so its icon keeps a fixed colour. */
        val tintIcon: Boolean = true
    )

    fun setup(activity: AppCompatActivity, current: NavTab) {
        val tabs = mapOf(
            NavTab.HOME to TabViews(
                activity.findViewById(R.id.navHome),
                activity.findViewById(R.id.navHomeIcon),
                activity.findViewById(R.id.navHomeLabel)
            ),
            NavTab.HISTORY to TabViews(
                activity.findViewById(R.id.navHistory),
                activity.findViewById(R.id.navHistoryIcon),
                activity.findViewById(R.id.navHistoryLabel)
            ),
            NavTab.LOG to TabViews(
                activity.findViewById(R.id.navLog),
                activity.findViewById(R.id.navLogIcon),
                activity.findViewById(R.id.navLogLabel),
                tintIcon = false
            ),
            NavTab.CHARTS to TabViews(
                activity.findViewById(R.id.navCharts),
                activity.findViewById(R.id.navChartsIcon),
                activity.findViewById(R.id.navChartsLabel)
            ),
            NavTab.RECORDS to TabViews(
                activity.findViewById(R.id.navRecords),
                activity.findViewById(R.id.navRecordsIcon),
                activity.findViewById(R.id.navRecordsLabel)
            )
        )

        tabs[NavTab.CHARTS]?.container?.setOnClickListener {
            if (current != NavTab.CHARTS) activity.startActivity(Intent(activity, ChartsActivity::class.java))
        }
        tabs[NavTab.RECORDS]?.container?.setOnClickListener {
            if (current != NavTab.RECORDS) activity.startActivity(Intent(activity, RecordsActivity::class.java))
        }
        tabs[NavTab.HOME]?.container?.setOnClickListener {
            if (current != NavTab.HOME) activity.finish()
        }
        tabs[NavTab.LOG]?.container?.setOnClickListener {
            if (current != NavTab.LOG) activity.startActivity(Intent(activity, LogWorkoutActivity::class.java))
        }
        tabs[NavTab.HISTORY]?.container?.setOnClickListener {
            if (current != NavTab.HISTORY) activity.startActivity(Intent(activity, HistoryActivity::class.java))
        }

        // Applied after the listeners, because setOnClickListener turns clickable
        // back on and the current tab should not show a ripple.
        val selected = ContextCompat.getColor(activity, R.color.brand_bright)
        val unselected = ContextCompat.getColor(activity, R.color.text_tertiary)
        tabs.forEach { (tab, views) ->
            val isCurrent = tab == current
            val tint = if (isCurrent) selected else unselected
            if (views.tintIcon) views.icon.setColorFilter(tint)
            views.label.setTextColor(tint)
            views.container.isSelected = isCurrent
            views.container.isClickable = !isCurrent
        }
    }
}
