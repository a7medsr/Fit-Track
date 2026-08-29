package com.example.fittrack.ui.community

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fittrack.R
import com.example.fittrack.domain.model.Community

/**
 * The hub list: section headings, communities, and the occasional line of
 * explanation when there is nothing to show.
 */
class CommunityListAdapter(
    private val onOpen: (Community) -> Unit,
    private val onJoin: (Community) -> Unit,
    private val onWithdraw: (Community) -> Unit
) : ListAdapter<HubRow, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is HubRow.Header -> TYPE_HEADER
        is HubRow.Item -> TYPE_COMMUNITY
        is HubRow.Note -> TYPE_NOTE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_exercise_header, parent, false)
            )
            TYPE_NOTE -> NoteViewHolder(
                inflater.inflate(R.layout.item_community_note, parent, false)
            )
            else -> CommunityViewHolder(
                inflater.inflate(R.layout.item_community, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is HubRow.Header -> (holder as HeaderViewHolder).bind(row)
            is HubRow.Note -> (holder as NoteViewHolder).bind(row)
            is HubRow.Item -> (holder as CommunityViewHolder).bind(row.community)
        }
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title = view as TextView
        fun bind(row: HubRow.Header) {
            title.text = row.title
        }
    }

    private class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text = view as TextView
        fun bind(row: HubRow.Note) {
            text.text = row.text
        }
    }

    private inner class CommunityViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: TextView = view.findViewById(R.id.communityIcon)
        private val name: TextView = view.findViewById(R.id.communityName)
        private val meta: TextView = view.findViewById(R.id.communityMeta)
        private val action: TextView = view.findViewById(R.id.communityAction)

        fun bind(community: Community) {
            icon.text = community.icon
            name.text = community.name
            meta.text = itemView.context.getString(
                R.string.community_meta,
                community.memberCount,
                community.metric.displayName
            )

            when {
                community.isMember -> {
                    action.setText(
                        if (community.isAdmin) R.string.community_role_admin
                        else R.string.community_action_open
                    )
                    action.isEnabled = true
                    action.setOnClickListener { onOpen(community) }
                }
                community.isBanned -> {
                    // Nothing they can do here, and pretending the Join button
                    // works only to have the write refused is worse than saying
                    // so up front.
                    action.setText(R.string.community_action_removed)
                    action.isEnabled = false
                    action.setOnClickListener(null)
                }
                community.hasPendingRequest -> {
                    action.setText(R.string.community_action_pending)
                    action.isEnabled = true
                    action.setOnClickListener { onWithdraw(community) }
                }
                else -> {
                    action.setText(R.string.community_action_join)
                    action.isEnabled = true
                    action.setOnClickListener { onJoin(community) }
                }
            }
            action.alpha = if (action.isEnabled) 1f else 0.45f

            // Only a member can see inside, so a non-member tapping the row
            // would only ever meet a permission error.
            itemView.setOnClickListener {
                if (community.isMember) onOpen(community) else onJoin(community)
            }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_COMMUNITY = 1
        const val TYPE_NOTE = 2

        val DIFF = object : DiffUtil.ItemCallback<HubRow>() {
            override fun areItemsTheSame(oldItem: HubRow, newItem: HubRow): Boolean = when {
                oldItem is HubRow.Header && newItem is HubRow.Header ->
                    oldItem.title == newItem.title
                oldItem is HubRow.Note && newItem is HubRow.Note -> true
                oldItem is HubRow.Item && newItem is HubRow.Item ->
                    oldItem.community.id == newItem.community.id
                else -> false
            }

            override fun areContentsTheSame(oldItem: HubRow, newItem: HubRow): Boolean =
                oldItem == newItem
        }
    }
}
