package com.example.fittrack.ui.common

import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.fittrack.MainActivity
import com.example.fittrack.R
import com.example.fittrack.ui.charts.ChartsActivity
import com.example.fittrack.ui.community.CommunityHubActivity
import com.example.fittrack.ui.records.RecordsActivity
import com.example.fittrack.ui.logworkout.LogWorkoutActivity
import com.example.fittrack.ui.history.HistoryActivity

enum class NavTab { HOME, HISTORY, LOG, CHARTS, RECORDS, COMMUNITY }

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
            ),
            NavTab.COMMUNITY to TabViews(
                activity.findViewById(R.id.navCommunity),
                activity.findViewById(R.id.navCommunityIcon),
                activity.findViewById(R.id.navCommunityLabel)
            )
        )

        tabs.forEach { (tab, views) ->
            views.container.setOnClickListener { navigateTo(activity, tab) }
        }

        // Applied after the listeners so the tint is not overwritten. Every tab
        // stays clickable, including the current one: sub-screens such as the
        // exercise picker mark their parent tab as current, and tapping it has
        // to be the way back to that parent.
        val selected = ContextCompat.getColor(activity, R.color.brand_bright)
        val unselected = ContextCompat.getColor(activity, R.color.text_tertiary)
        tabs.forEach { (tab, views) ->
            val isCurrent = tab == current
            val tint = if (isCurrent) selected else unselected
            if (views.tintIcon) views.icon.setColorFilter(tint)
            views.label.setTextColor(tint)
            views.container.isSelected = isCurrent
        }
    }

    /**
     * CLEAR_TOP pops everything stacked above the destination and SINGLE_TOP
     * reuses the instance already sitting there, so one tap always lands on the
     * tab -- and browsing between tabs cannot pile up an ever deeper back stack.
     */
    private fun navigateTo(activity: AppCompatActivity, tab: NavTab) {
        val target = destinations[tab] ?: return
        if (activity.javaClass == target) return
        activity.startActivity(
            Intent(activity, target).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
    }

    private val destinations = mapOf<NavTab, Class<*>>(
        NavTab.HOME to MainActivity::class.java,
        NavTab.HISTORY to HistoryActivity::class.java,
        NavTab.LOG to LogWorkoutActivity::class.java,
        NavTab.CHARTS to ChartsActivity::class.java,
        NavTab.RECORDS to RecordsActivity::class.java,
        NavTab.COMMUNITY to CommunityHubActivity::class.java
    )
}
