package com.example.fittrack.ui.community

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fittrack.R
import com.example.fittrack.domain.model.CommunityMetric
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Name, emoji, description and the metric the weekly board will rank on. */
@AndroidEntryPoint
class CreateCommunityActivity : AppCompatActivity() {

    private val viewModel: CreateCommunityViewModel by viewModels()

    private lateinit var iconRow: LinearLayout
    private lateinit var metricRow: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var createBtn: TextView
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_community)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBar)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<TextView>(R.id.screenTitle).setText(R.string.community_create_title)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        iconRow = findViewById(R.id.iconRow)
        metricRow = findViewById(R.id.metricRow)
        nameInput = findViewById(R.id.nameInput)
        descriptionInput = findViewById(R.id.descriptionInput)
        createBtn = findViewById(R.id.createBtn)
        errorText = findViewById(R.id.errorText)

        buildIconRow()
        buildMetricRow()

        createBtn.setOnClickListener {
            viewModel.create(
                nameInput.text.toString(),
                descriptionInput.text.toString()
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is CreateCommunityEvent.Created -> showCode(event)
                        }
                    }
                }
            }
        }
    }

    private fun render(state: CreateCommunityState) {
        for (i in 0 until iconRow.childCount) {
            val chip = iconRow.getChildAt(i) as TextView
            chip.isSelected = chip.text.toString() == state.icon
            chip.setBackgroundResource(
                if (chip.isSelected) R.drawable.bg_chip_category_selected
                else R.drawable.bg_chip_category
            )
        }
        for (i in 0 until metricRow.childCount) {
            val option = metricRow.getChildAt(i)
            val metric = option.tag as CommunityMetric
            option.isSelected = metric == state.metric
            option.setBackgroundResource(
                if (option.isSelected) R.drawable.bg_card_accent else R.drawable.bg_card
            )
        }

        createBtn.isEnabled = !state.busy
        createBtn.alpha = if (state.busy) 0.5f else 1f
        createBtn.setText(
            if (state.busy) R.string.community_creating else R.string.community_action_create
        )

        errorText.text = state.error.orEmpty()
        errorText.visibility = if (state.error == null) View.GONE else View.VISIBLE
    }

    private fun buildIconRow() {
        ICONS.forEach { icon ->
            val chip = TextView(this).apply {
                text = icon
                textSize = 20f
                gravity = android.view.Gravity.CENTER
                includeFontPadding = false
                setOnClickListener { viewModel.pickIcon(icon) }
            }
            val size = resources.getDimensionPixelSize(R.dimen.touch_target)
            val params = LinearLayout.LayoutParams(size, size)
            params.marginEnd = resources.getDimensionPixelSize(R.dimen.space_sm)
            iconRow.addView(chip, params)
        }
    }

    private fun buildMetricRow() {
        CommunityMetric.entries.forEach { metric ->
            val option = layoutInflater.inflate(R.layout.item_metric_option, metricRow, false)
            option.tag = metric
            option.findViewById<TextView>(R.id.metricName).text = metric.displayName
            option.findViewById<TextView>(R.id.metricDescription).setText(metric.explainer())
            option.setOnClickListener { viewModel.pickMetric(metric) }
            metricRow.addView(option)
        }
    }

    /**
     * The code is the only way anyone else finds the group by hand, and it is
     * generated by the server rather than chosen, so it is shown once, clearly,
     * at the moment it exists.
     */
    private fun showCode(event: CreateCommunityEvent.Created) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.community_created_title, event.name))
            .setMessage(getString(R.string.community_created_body, event.communityId))
            .setPositiveButton(R.string.community_action_open) { _, _ ->
                startActivity(CommunityDetailActivity.intent(this, event.communityId))
                finish()
            }
            .setCancelable(false)
            .show()
    }
}

/** One line explaining what a metric actually counts. */
fun CommunityMetric.explainer(): Int = when (this) {
    CommunityMetric.STEPS -> R.string.community_metric_steps_help
    CommunityMetric.ACTIVE_MINUTES -> R.string.community_metric_minutes_help
    CommunityMetric.CALORIES -> R.string.community_metric_calories_help
    CommunityMetric.WORKOUTS -> R.string.community_metric_workouts_help
}
