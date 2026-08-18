package com.example.fittrack.ui.common

import android.content.Intent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.fittrack.R
import com.example.fittrack.ui.charts.ChartsActivity
import com.example.fittrack.ui.records.RecordsActivity

enum class NavTab { HOME, HISTORY, LOG, CHARTS, RECORDS }

object NavBarHelper {
    fun setup(activity: AppCompatActivity, current: NavTab) {
        val home = activity.findViewById<TextView>(R.id.navHome)
        val history = activity.findViewById<TextView>(R.id.navHistory)
        val log = activity.findViewById<TextView>(R.id.navLog)
        val charts = activity.findViewById<TextView>(R.id.navCharts)
        val records = activity.findViewById<TextView>(R.id.navRecords)

        val tabs = mapOf(
            NavTab.HOME to home, NavTab.HISTORY to history, NavTab.LOG to log,
            NavTab.CHARTS to charts, NavTab.RECORDS to records
        )
        tabs.forEach { (tab, view) ->
            view.setTextColor(
                ContextCompat.getColor(activity, if (tab == current) R.color.accent_blue else R.color.text_secondary)
            )
        }

        charts.setOnClickListener {
            if (current != NavTab.CHARTS) activity.startActivity(Intent(activity, ChartsActivity::class.java))
        }
        records.setOnClickListener {
            if (current != NavTab.RECORDS) activity.startActivity(Intent(activity, RecordsActivity::class.java))
        }
        home.setOnClickListener {
            if (current != NavTab.HOME) activity.finish()
        }
    }
}