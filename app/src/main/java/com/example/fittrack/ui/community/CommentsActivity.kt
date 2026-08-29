package com.example.fittrack.ui.community

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fittrack.R
import com.example.fittrack.domain.model.PostComment
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** A post's comments, oldest at the top, with the composer pinned underneath. */
@AndroidEntryPoint
class CommentsActivity : AppCompatActivity() {

    private val viewModel: CommentsViewModel by viewModels()

    private lateinit var list: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var input: EditText
    private lateinit var sendBtn: ImageView

    private val adapter = CommentAdapter(onDelete = ::confirmDelete)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_comments)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBar)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<TextView>(R.id.screenTitle).setText(R.string.community_comments_title)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        list = findViewById(R.id.commentList)
        list.layoutManager = LinearLayoutManager(this).apply {
            // Anchored at the bottom, where a conversation is read from.
            stackFromEnd = true
        }
        list.adapter = adapter

        progress = findViewById(R.id.progress)
        emptyText = findViewById(R.id.emptyText)
        input = findViewById(R.id.commentInput)
        sendBtn = findViewById(R.id.sendBtn)

        sendBtn.setOnClickListener {
            val text = input.text.toString()
            if (text.isBlank()) return@setOnClickListener
            input.setText("")
            viewModel.send(text)
        }

        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Older comments are above, so paging happens on the way up.
                if (dy >= 0) return
                val layout = recyclerView.layoutManager as LinearLayoutManager
                if (layout.findFirstVisibleItemPosition() <= PREFETCH_ROWS) viewModel.loadOlder()
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch {
                    viewModel.sending.collect { busy ->
                        sendBtn.isEnabled = !busy
                        sendBtn.alpha = if (busy) 0.5f else 1f
                    }
                }
                launch {
                    viewModel.messages.collect {
                        Toast.makeText(this@CommentsActivity, it, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun render(state: UiState<List<PostComment>>) {
        progress.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
        emptyText.visibility = View.GONE
        list.visibility = View.VISIBLE

        when (state) {
            is UiState.Loading -> Unit

            is UiState.Success -> {
                adapter.submitList(state.data) {
                    // After sending, the new comment is at the bottom and the
                    // author expects to see it without scrolling.
                    list.scrollToPosition(adapter.itemCount - 1)
                }
            }

            is UiState.Empty -> {
                adapter.submitList(emptyList())
                showMessage(getString(R.string.community_comments_empty))
            }

            is UiState.Error -> {
                adapter.submitList(emptyList())
                showMessage(state.message)
            }
        }
    }

    private fun showMessage(text: String) {
        list.visibility = View.GONE
        emptyText.visibility = View.VISIBLE
        emptyText.text = text
    }

    private fun confirmDelete(comment: PostComment) {
        AlertDialog.Builder(this)
            .setMessage(R.string.community_delete_comment_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete(comment) }
            .show()
    }

    companion object {
        const val EXTRA_COMMUNITY = "communityId"
        const val EXTRA_POST = "postId"
        private const val PREFETCH_ROWS = 3

        fun intent(context: Context, communityId: String, postId: String): Intent =
            Intent(context, CommentsActivity::class.java)
                .putExtra(EXTRA_COMMUNITY, communityId)
                .putExtra(EXTRA_POST, postId)
    }
}
