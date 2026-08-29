package com.example.fittrack.ui.community

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fittrack.R
import com.example.fittrack.domain.model.CommunityMetric
import com.example.fittrack.domain.model.LeaderboardEntry
import com.example.fittrack.ui.common.AvatarLoader
import com.example.fittrack.ui.common.RelativeTime
import java.text.NumberFormat

class LeaderboardAdapter : ListAdapter<LeaderboardEntry, LeaderboardAdapter.EntryViewHolder>(DIFF) {

    var metric: CommunityMetric = CommunityMetric.DEFAULT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = EntryViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_leaderboard, parent, false)
    )

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EntryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val rank: TextView = view.findViewById(R.id.rank)
        private val avatar: ImageView = view.findViewById(R.id.memberAvatar)
        private val initials: TextView = view.findViewById(R.id.memberInitials)
        private val name: TextView = view.findViewById(R.id.memberName)
        private val freshness: TextView = view.findViewById(R.id.memberFreshness)
        private val value: TextView = view.findViewById(R.id.memberValue)

        fun bind(entry: LeaderboardEntry) {
            // A medal for the top three, a number for everyone else. Ties share
            // a rank, so there can be two silvers and no bronze.
            rank.text = when (entry.rank) {
                1 -> "🥇"
                2 -> "🥈"
                3 -> "🥉"
                else -> entry.rank.toString()
            }

            AvatarLoader.bind(avatar, initials, entry.avatarUrl, entry.name)
            name.text = if (entry.isMe) {
                itemView.context.getString(R.string.community_you, entry.name)
            } else {
                entry.name
            }
            freshness.text = RelativeTime.freshness(entry.updatedAt)
            value.text = itemView.context.getString(
                R.string.community_metric_value,
                NUMBER.format(entry.value),
                metric.unit
            )

            // The viewer's own row is tinted so they can find themselves in a
            // list of fifty without reading every name.
            itemView.setBackgroundResource(
                if (entry.isMe) R.drawable.bg_card_accent else R.drawable.bg_card
            )
        }
    }

    private companion object {
        val NUMBER: NumberFormat = NumberFormat.getIntegerInstance()

        val DIFF = object : DiffUtil.ItemCallback<LeaderboardEntry>() {
            override fun areItemsTheSame(oldItem: LeaderboardEntry, newItem: LeaderboardEntry) =
                oldItem.uid == newItem.uid

            override fun areContentsTheSame(oldItem: LeaderboardEntry, newItem: LeaderboardEntry) =
                oldItem == newItem
        }
    }
}
