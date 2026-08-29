package com.example.fittrack.ui.community

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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
import com.example.fittrack.domain.model.CommunityMember
import com.example.fittrack.domain.model.CommunityMetric
import com.example.fittrack.domain.model.JoinRequest
import com.example.fittrack.ui.common.AvatarLoader
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Everything only the admin can do: approve or reject requests, remove members,
 * hand the community over, change what the board ranks on, and delete it.
 */
@AndroidEntryPoint
class ManageCommunityActivity : AppCompatActivity() {

    private val viewModel: ManageCommunityViewModel by viewModels()

    private lateinit var content: View
    private lateinit var progress: ProgressBar
    private lateinit var requestsHeader: TextView
    private lateinit var requestsList: LinearLayout
    private lateinit var membersList: LinearLayout
    private lateinit var bannedHeader: TextView
    private lateinit var bannedList: LinearLayout
    private lateinit var metricList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_manage_community)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBar)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<TextView>(R.id.screenTitle).setText(R.string.community_manage_title)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        content = findViewById(R.id.content)
        progress = findViewById(R.id.progress)
        requestsHeader = findViewById(R.id.requestsHeader)
        requestsList = findViewById(R.id.requestsList)
        membersList = findViewById(R.id.membersList)
        bannedHeader = findViewById(R.id.bannedHeader)
        bannedList = findViewById(R.id.bannedList)
        metricList = findViewById(R.id.metricList)

        findViewById<View>(R.id.deleteBtn).setOnClickListener { confirmDeleteCommunity() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun render(state: UiState<ManageState>) {
        progress.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
        content.visibility = if (state is UiState.Success) View.VISIBLE else View.GONE

        when (state) {
            is UiState.Success -> bind(state.data)
            is UiState.Error -> Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            else -> Unit
        }
    }

    private fun bind(data: ManageState) {
        bindRequests(data.requests)
        bindMembers(data.members)
        bindBanned(data.banned)
        bindMetrics(data.community.metric)
    }

    private fun bindRequests(requests: List<JoinRequest>) {
        requestsList.removeAllViews()
        requestsHeader.visibility = if (requests.isEmpty()) View.GONE else View.VISIBLE

        requests.forEach { request ->
            val row = inflateRow(requestsList, request.name, request.avatarUrl, null)
            row.primary(R.string.community_action_approve) { viewModel.approve(request) }
            row.secondary(R.string.community_action_reject) { viewModel.reject(request) }
            requestsList.addView(row.view)
        }
    }

    private fun bindMembers(members: List<CommunityMember>) {
        membersList.removeAllViews()

        members.forEach { member ->
            val meta = if (member.isAdmin) getString(R.string.community_role_admin) else null
            val row = inflateRow(membersList, member.name, member.avatarUrl, meta)

            if (!member.isAdmin) {
                row.primary(R.string.community_action_make_admin) { confirmTransfer(member) }
                row.secondary(R.string.community_action_remove) { confirmRemove(member) }
            }
            membersList.addView(row.view)
        }
    }

    private fun bindBanned(banned: List<CommunityMember>) {
        bannedList.removeAllViews()
        bannedHeader.visibility = if (banned.isEmpty()) View.GONE else View.VISIBLE

        banned.forEach { member ->
            // Only the uid survives a removal, so there is no name or photo to
            // show. The short id is enough for an admin to tell two apart.
            val label = getString(R.string.community_banned_row, member.uid.take(6))
            val row = inflateRow(bannedList, label, null, null)
            row.primary(R.string.community_action_unban) { viewModel.unban(member) }
            bannedList.addView(row.view)
        }
    }

    private fun bindMetrics(current: CommunityMetric) {
        metricList.removeAllViews()

        CommunityMetric.entries.forEach { metric ->
            val option = layoutInflater.inflate(R.layout.item_metric_option, metricList, false)
            option.findViewById<TextView>(R.id.metricName).text = metric.displayName
            option.findViewById<TextView>(R.id.metricDescription).setText(metric.explainer())
            option.setBackgroundResource(
                if (metric == current) R.drawable.bg_card_accent else R.drawable.bg_card
            )
            option.setOnClickListener {
                if (metric != current) viewModel.setMetric(metric)
            }
            metricList.addView(option)
        }
    }

    /** A person row with two optional action buttons. */
    private fun inflateRow(
        parent: LinearLayout,
        name: String,
        avatarUrl: String?,
        meta: String?
    ): MemberRow {
        val view = layoutInflater.inflate(R.layout.item_member_row, parent, false)
        AvatarLoader.bind(
            view.findViewById(R.id.rowAvatar),
            view.findViewById(R.id.rowInitials),
            avatarUrl,
            name
        )
        view.findViewById<TextView>(R.id.rowName).text = name
        view.findViewById<TextView>(R.id.rowMeta).apply {
            text = meta.orEmpty()
            visibility = if (meta == null) View.GONE else View.VISIBLE
        }
        return MemberRow(view)
    }

    private class MemberRow(val view: View) {
        fun primary(label: Int, action: () -> Unit) = button(R.id.rowPrimaryBtn, label, action)
        fun secondary(label: Int, action: () -> Unit) = button(R.id.rowSecondaryBtn, label, action)

        private fun button(id: Int, label: Int, action: () -> Unit) {
            view.findViewById<TextView>(id).apply {
                setText(label)
                visibility = View.VISIBLE
                setOnClickListener { action() }
            }
        }
    }

    private fun handleEvent(event: ManageEvent) {
        when (event) {
            is ManageEvent.Message -> Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()
            is ManageEvent.Closed -> finish()
        }
    }

    private fun confirmRemove(member: CommunityMember) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.community_remove_title, member.name))
            .setMessage(R.string.community_remove_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.community_action_remove) { _, _ -> viewModel.remove(member) }
            .show()
    }

    private fun confirmTransfer(member: CommunityMember) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.community_transfer_title, member.name))
            .setMessage(R.string.community_transfer_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.community_action_make_admin) { _, _ ->
                viewModel.transferAdmin(member)
            }
            .show()
    }

    /**
     * Deleting takes every post, comment and photo with it and cannot be
     * undone, so it asks for the community's own code rather than a yes.
     */
    private fun confirmDeleteCommunity() {
        val community = (viewModel.state.value as? UiState.Success)?.data?.community ?: return

        val input = android.widget.EditText(this).apply {
            hint = community.id
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.community_action_delete)
            .setMessage(getString(R.string.community_delete_confirm, community.id))
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (input.text.toString().trim().equals(community.id, ignoreCase = true)) {
                    viewModel.deleteCommunity()
                } else {
                    Toast.makeText(this, R.string.community_delete_mismatch, Toast.LENGTH_LONG)
                        .show()
                }
            }
            .show()
    }

    companion object {
        const val EXTRA_ID = "communityId"

        fun intent(context: Context, communityId: String): Intent =
            Intent(context, ManageCommunityActivity::class.java).putExtra(EXTRA_ID, communityId)
    }
}
