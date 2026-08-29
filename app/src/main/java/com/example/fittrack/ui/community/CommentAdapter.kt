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
import com.example.fittrack.domain.model.PostComment
import com.example.fittrack.ui.common.AvatarLoader
import com.example.fittrack.ui.common.RelativeTime

class CommentAdapter(
    private val onDelete: (PostComment) -> Unit
) : ListAdapter<PostComment, CommentAdapter.CommentViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = CommentViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
    )

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar: ImageView = view.findViewById(R.id.commentAvatar)
        private val initials: TextView = view.findViewById(R.id.commentInitials)
        private val author: TextView = view.findViewById(R.id.commentAuthor)
        private val time: TextView = view.findViewById(R.id.commentTime)
        private val text: TextView = view.findViewById(R.id.commentText)

        fun bind(comment: PostComment) {
            AvatarLoader.bind(avatar, initials, comment.authorAvatarUrl, comment.authorName)
            author.text = comment.authorName
            time.text = RelativeTime.shortLabel(comment.createdAt)
            text.text = comment.text

            // Long press rather than a visible button: a delete control on
            // every comment would dominate a list of short one-line replies.
            itemView.setOnLongClickListener {
                if (comment.canDelete) {
                    onDelete(comment)
                    true
                } else {
                    false
                }
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<PostComment>() {
            override fun areItemsTheSame(oldItem: PostComment, newItem: PostComment) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PostComment, newItem: PostComment) =
                oldItem == newItem
        }
    }
}
