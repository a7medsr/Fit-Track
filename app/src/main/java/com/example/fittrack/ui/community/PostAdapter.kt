package com.example.fittrack.ui.community

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.fittrack.R
import com.example.fittrack.domain.model.CommunityPost
import com.example.fittrack.domain.model.Reaction
import com.example.fittrack.ui.common.AvatarLoader
import com.example.fittrack.ui.common.RelativeTime

class PostAdapter(
    private val onReact: (CommunityPost, Reaction) -> Unit,
    private val onComments: (CommunityPost) -> Unit,
    private val onDelete: (CommunityPost) -> Unit
) : ListAdapter<CommunityPost, PostAdapter.PostViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PostViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
    )

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar: ImageView = view.findViewById(R.id.authorAvatar)
        private val initials: TextView = view.findViewById(R.id.authorInitials)
        private val name: TextView = view.findViewById(R.id.authorName)
        private val time: TextView = view.findViewById(R.id.postTime)
        private val text: TextView = view.findViewById(R.id.postText)
        private val image: ImageView = view.findViewById(R.id.postImage)
        private val reactionRow: LinearLayout = view.findViewById(R.id.reactionRow)
        private val commentBtn: TextView = view.findViewById(R.id.commentBtn)
        private val deleteBtn: TextView = view.findViewById(R.id.deleteBtn)

        fun bind(post: CommunityPost) {
            AvatarLoader.bind(avatar, initials, post.authorAvatarUrl, post.authorName)
            name.text = post.authorName
            time.text = RelativeTime.shortLabel(post.createdAt)
            text.text = post.text

            if (post.imageUrl.isNullOrBlank()) {
                // Cleared as well as hidden: a recycled row would otherwise
                // keep the previous post's photo in memory and flash it into
                // the next one that does have an image.
                image.visibility = View.GONE
                image.load(null as String?)
            } else {
                image.visibility = View.VISIBLE
                image.load(post.imageUrl) { crossfade(true) }
            }

            commentBtn.text = when (post.commentCount) {
                0 -> itemView.context.getString(R.string.community_comment_none)
                1 -> itemView.context.getString(R.string.community_comment_one)
                else -> itemView.context.getString(
                    R.string.community_comment_many,
                    post.commentCount
                )
            }
            commentBtn.setOnClickListener { onComments(post) }

            deleteBtn.visibility = if (post.canDelete) View.VISIBLE else View.GONE
            deleteBtn.setOnClickListener { onDelete(post) }

            bindReactions(post)
        }

        /**
         * A chip per reaction, always all four, so reacting is one tap rather
         * than a tap to open a picker and another to choose. The count is only
         * drawn once someone has actually used that one.
         */
        private fun bindReactions(post: CommunityPost) {
            reactionRow.removeAllViews()
            val inflater = LayoutInflater.from(itemView.context)

            Reaction.entries.forEach { reaction ->
                val chip = inflater.inflate(R.layout.item_reaction, reactionRow, false) as TextView
                val count = post.reactionCounts[reaction] ?: 0
                chip.text = if (count > 0) "${reaction.emoji} $count" else reaction.emoji

                val chosen = post.myReaction == reaction
                chip.setBackgroundResource(
                    if (chosen) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected
                )
                chip.setTextColor(
                    itemView.context.getColor(
                        if (chosen) R.color.brand_bright else R.color.text_secondary
                    )
                )
                chip.setOnClickListener { onReact(post, reaction) }
                reactionRow.addView(chip)
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<CommunityPost>() {
            override fun areItemsTheSame(oldItem: CommunityPost, newItem: CommunityPost) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CommunityPost, newItem: CommunityPost) =
                oldItem == newItem
        }
    }
}
