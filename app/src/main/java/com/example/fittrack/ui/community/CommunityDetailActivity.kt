package com.example.fittrack.ui.community

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
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
import com.example.fittrack.domain.model.Community
import com.example.fittrack.domain.model.CommunityPost
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat

/** One community: its feed and its weekly leaderboard. */
@AndroidEntryPoint
class CommunityDetailActivity : AppCompatActivity() {

    private val viewModel: CommunityDetailViewModel by viewModels()

    private lateinit var list: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var feedTab: TextView
    private lateinit var boardTab: TextView
    private lateinit var boardHeader: View
    private lateinit var boardWeek: TextView
    private lateinit var lastWeekWinner: TextView
    private lateinit var newPostBtn: TextView
    private lateinit var manageBtn: TextView

    private val postAdapter = PostAdapter(
        onReact = { post, reaction -> viewModel.react(post, reaction) },
        onComments = { post ->
            startActivity(CommentsActivity.intent(this, viewModel.communityId, post.id))
        },
        onDelete = ::confirmDeletePost
    )
    private val boardAdapter = LeaderboardAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_community_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBar)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        list = findViewById(R.id.contentList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = postAdapter

        progress = findViewById(R.id.progress)
        emptyText = findViewById(R.id.emptyText)
        feedTab = findViewById(R.id.feedTab)
        boardTab = findViewById(R.id.boardTab)
        boardHeader = findViewById(R.id.boardHeader)
        boardWeek = findViewById(R.id.boardWeek)
        lastWeekWinner = findViewById(R.id.lastWeekWinner)
        newPostBtn = findViewById(R.id.newPostBtn)
        manageBtn = findViewById(R.id.manageBtn)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        feedTab.setOnClickListener { viewModel.switchTab(CommunityTab.FEED) }
        boardTab.setOnClickListener { viewModel.switchTab(CommunityTab.LEADERBOARD) }
        newPostBtn.setOnClickListener {
            startActivity(PostComposerActivity.intent(this, viewModel.communityId))
        }
        manageBtn.setOnClickListener { openMenu() }

        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (viewModel.tab.value != CommunityTab.FEED || dy <= 0) return
                val layout = recyclerView.layoutManager as LinearLayoutManager
                // Fetches the next page a few rows before the bottom, so the
                // list does not visibly stop while it loads.
                if (layout.findLastVisibleItemPosition() >= layout.itemCount - PREFETCH_ROWS) {
                    viewModel.loadMore()
                }
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.community.collect(::renderCommunity) }
                launch { viewModel.tab.collect { renderTab(it) } }
                launch { viewModel.feed.collect { if (isFeed()) renderFeed(it) } }
                launch { viewModel.board.collect { if (!isFeed()) renderBoard(it) } }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A new post, a comment, or an admin action all happen on another
        // screen and change what this one should show.
        viewModel.refresh()
    }

    private fun isFeed() = viewModel.tab.value == CommunityTab.FEED

    private fun renderCommunity(community: Community?) {
        findViewById<TextView>(R.id.screenTitle).text =
            community?.let { "${it.icon}  ${it.name}" } ?: getString(R.string.community_title)
        boardAdapter.metric = community?.metric ?: boardAdapter.metric
    }

    private fun renderTab(tab: CommunityTab) {
        val feedSelected = tab == CommunityTab.FEED

        feedTab.setBackgroundResource(
            if (feedSelected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected
        )
        feedTab.setTextColor(
            getColor(if (feedSelected) R.color.brand_bright else R.color.text_secondary)
        )
        boardTab.setBackgroundResource(
            if (feedSelected) R.drawable.bg_chip_unselected else R.drawable.bg_chip_selected
        )
        boardTab.setTextColor(
            getColor(if (feedSelected) R.color.text_secondary else R.color.brand_bright)
        )

        boardHeader.visibility = if (feedSelected) View.GONE else View.VISIBLE
        newPostBtn.visibility = if (feedSelected) View.VISIBLE else View.GONE

        list.adapter = if (feedSelected) postAdapter else boardAdapter
        if (feedSelected) renderFeed(viewModel.feed.value) else renderBoard(viewModel.board.value)
    }

    private fun renderFeed(state: UiState<List<CommunityPost>>) {
        progress.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
        emptyText.visibility = View.GONE
        list.visibility = View.VISIBLE

        when (state) {
            is UiState.Loading -> Unit
            is UiState.Success -> postAdapter.submitList(state.data)
            is UiState.Empty -> {
                postAdapter.submitList(emptyList())
                showEmpty(getString(R.string.community_feed_empty))
            }
            is UiState.Error -> {
                postAdapter.submitList(emptyList())
                showEmpty(state.message)
            }
        }
    }

    private fun renderBoard(state: UiState<com.example.fittrack.domain.repository.Leaderboard>) {
        progress.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
        emptyText.visibility = View.GONE
        list.visibility = View.VISIBLE

        when (state) {
            is UiState.Loading -> Unit

            is UiState.Success -> {
                val board = state.data
                boardAdapter.metric = board.metric
                boardWeek.text = getString(
                    R.string.community_board_week,
                    board.weekLabel,
                    board.metric.displayName
                )
                val winner = board.lastWeekWinner
                lastWeekWinner.visibility = if (winner == null) View.GONE else View.VISIBLE
                if (winner != null) {
                    lastWeekWinner.text = getString(
                        R.string.community_last_week,
                        winner.name,
                        NumberFormat.getIntegerInstance().format(winner.value),
                        winner.metric.unit
                    )
                }
                boardAdapter.submitList(board.entries)
                if (board.entries.isEmpty()) showEmpty(getString(R.string.community_board_empty))
            }

            is UiState.Empty -> showEmpty(getString(R.string.community_board_empty))
            is UiState.Error -> showEmpty(state.message)
        }
    }

    private fun showEmpty(text: String) {
        list.visibility = View.GONE
        emptyText.visibility = View.VISIBLE
        emptyText.text = text
    }

    private fun handleEvent(event: DetailEvent) {
        when (event) {
            is DetailEvent.Message ->
                Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()

            is DetailEvent.Closed -> {
                Toast.makeText(this, R.string.community_no_longer_member, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun confirmDeletePost(post: CommunityPost) {
        AlertDialog.Builder(this)
            .setMessage(R.string.community_delete_post_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deletePost(post) }
            .show()
    }

    /**
     * One menu for both roles. An admin manages the group; everyone else gets
     * the code to share and the way out.
     */
    private fun openMenu() {
        val community = viewModel.community.value ?: return
        val options = buildList {
            add(getString(R.string.community_menu_share_code))
            if (community.isAdmin) {
                add(getString(R.string.community_menu_manage))
            } else {
                add(getString(R.string.community_menu_leave))
            }
        }

        AlertDialog.Builder(this)
            .setItems(options.toTypedArray()) { _, index ->
                when (index) {
                    0 -> showCode(community)
                    1 -> if (community.isAdmin) {
                        startActivity(ManageCommunityActivity.intent(this, community.id))
                    } else {
                        confirmLeave(community)
                    }
                }
            }
            .show()
    }

    private fun showCode(community: Community) {
        AlertDialog.Builder(this)
            .setTitle(R.string.community_menu_share_code)
            .setMessage(getString(R.string.community_code_body, community.id))
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    private fun confirmLeave(community: Community) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.community_leave_title, community.name))
            .setMessage(R.string.community_leave_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.community_menu_leave) { _, _ -> viewModel.leave() }
            .show()
    }

    companion object {
        const val EXTRA_ID = "communityId"
        private const val PREFETCH_ROWS = 3

        fun intent(context: Context, communityId: String): Intent =
            Intent(context, CommunityDetailActivity::class.java).putExtra(EXTRA_ID, communityId)
    }
}
