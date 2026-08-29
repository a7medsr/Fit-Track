package com.example.fittrack.ui.community

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fittrack.R
import com.example.fittrack.ui.auth.SignInActivity
import com.example.fittrack.ui.common.NavBarHelper
import com.example.fittrack.ui.common.NavTab
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * The Community tab: the groups you are in, a public directory, and search by
 * name or by the six-character code.
 */
@AndroidEntryPoint
class CommunityHubActivity : AppCompatActivity() {

    private val viewModel: CommunityHubViewModel by viewModels()

    private lateinit var list: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var messageState: View
    private lateinit var messageText: TextView
    private lateinit var messageAction: TextView

    private val adapter = CommunityListAdapter(
        onOpen = { community ->
            startActivity(CommunityDetailActivity.intent(this, community.id))
        },
        onJoin = { community ->
            withName { confirmJoin(community.name) { viewModel.requestToJoin(community) } }
        },
        onWithdraw = { community -> viewModel.withdrawRequest(community) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_community_hub)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBar)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<TextView>(R.id.screenTitle).setText(R.string.community_title)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.createBtn).setOnClickListener { openCreate() }

        list = findViewById(R.id.communityList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        progress = findViewById(R.id.progress)
        messageState = findViewById(R.id.messageState)
        messageText = findViewById(R.id.messageText)
        messageAction = findViewById(R.id.messageAction)

        findViewById<EditText>(R.id.searchInput).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.search(s?.toString().orEmpty())
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })

        NavBarHelper.setup(this, NavTab.COMMUNITY)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch {
                    viewModel.messages.collect {
                        Toast.makeText(this@CommunityHubActivity, it, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from a community that was left, deleted or just joined,
        // the list behind it is out of date. Reloading here is cheaper than
        // holding a live listener open on the whole directory.
        viewModel.load()
    }

    private fun render(state: UiState<List<HubRow>>) {
        progress.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
        messageState.visibility = View.GONE
        list.visibility = View.VISIBLE

        when (state) {
            is UiState.Loading -> Unit

            is UiState.Success -> adapter.submitList(state.data)

            is UiState.Empty -> {
                adapter.submitList(emptyList())
                showMessage(getString(R.string.community_empty), R.string.action_retry) {
                    viewModel.load()
                }
            }

            is UiState.Error -> {
                adapter.submitList(emptyList())
                if (!viewModel.isSignedIn) {
                    showMessage(getString(R.string.community_signed_out), R.string.auth_sign_in_short) {
                        startActivity(Intent(this, SignInActivity::class.java))
                    }
                } else {
                    showMessage(state.message, R.string.action_retry) { viewModel.load() }
                }
            }
        }
    }

    private fun showMessage(text: String, actionLabel: Int, action: () -> Unit) {
        list.visibility = View.GONE
        messageState.visibility = View.VISIBLE
        messageText.text = text
        messageAction.setText(actionLabel)
        messageAction.setOnClickListener { action() }
    }

    /**
     * Joining publishes the user's name, photo and weekly number to people they
     * may not know, so it is worth one sentence before it happens rather than a
     * surprise afterwards.
     */
    private fun confirmJoin(name: String, onConfirm: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.community_join_title, name))
            .setMessage(R.string.community_join_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.community_action_request) { _, _ -> onConfirm() }
            .show()
    }

    private fun openCreate() {
        withName { startActivity(Intent(this, CreateCommunityActivity::class.java)) }
    }

    /**
     * Everything in a community is attributed, so nothing that puts the user in
     * front of other people happens until they have a name. Accounts created
     * before sign-up collected one are asked here rather than being turned away.
     */
    private fun withName(action: () -> Unit) {
        if (!viewModel.needsName) {
            action()
            return
        }
        NamePromptDialog.show(this) { name -> viewModel.saveName(name, action) }
    }
}
