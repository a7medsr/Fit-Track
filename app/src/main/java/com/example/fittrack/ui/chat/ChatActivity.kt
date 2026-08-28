package com.example.fittrack.ui.chat

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fittrack.R
import com.example.fittrack.domain.repository.ChatError
import com.example.fittrack.ui.common.NavBarHelper
import com.example.fittrack.ui.common.NavTab
import com.example.fittrack.ui.common.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {

    private val viewModel: ChatViewModel by viewModels()

    private lateinit var adapter: ChatAdapter
    private lateinit var chatList: RecyclerView
    private lateinit var stateView: View
    private lateinit var stateTitle: TextView
    private lateinit var stateBody: TextView
    private lateinit var messageInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        findViewById<TextView>(R.id.screenTitle).setText(R.string.chat_title)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        NavBarHelper.setup(this, NavTab.HOME)

        chatList = findViewById(R.id.chatList)
        stateView = findViewById(R.id.stateView)
        stateTitle = findViewById(R.id.stateTitle)
        stateBody = findViewById(R.id.stateBody)
        messageInput = findViewById(R.id.messageInput)

        adapter = ChatAdapter(
            onConfirm = { viewModel.confirm(it.id) },
            onCancel = { viewModel.cancel(it.id) }
        )
        chatList.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        chatList.adapter = adapter

        findViewById<View>(R.id.sendBtn).setOnClickListener { send() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.errors.collect(::showError) }
            }
        }
    }

    private fun send() {
        val text = messageInput.text.toString()
        if (text.isBlank()) return
        messageInput.setText("")
        viewModel.send(text)
    }

    private fun render(state: UiState<ChatScreenState>) {
        when (state) {
            is UiState.Loading -> Unit

            is UiState.Empty -> {
                stateTitle.setText(R.string.chat_empty_title)
                stateBody.setText(R.string.chat_empty_body)
                stateView.visibility = View.VISIBLE
                chatList.visibility = View.GONE
            }

            is UiState.Error -> {
                stateTitle.setText(R.string.chat_error_title)
                stateBody.text = state.message
                stateView.visibility = View.VISIBLE
                chatList.visibility = View.GONE
            }

            is UiState.Success -> {
                stateView.visibility = View.GONE
                chatList.visibility = View.VISIBLE
                adapter.confirmPrompts = state.data.confirmPrompts
                adapter.submitList(state.data.messages) {
                    if (state.data.messages.isNotEmpty()) {
                        chatList.scrollToPosition(state.data.messages.lastIndex)
                    }
                }
            }
        }
    }

    /**
     * Tier 1 keeps working without a key or a network, so these only ever
     * surface for the model-backed tiers. Say which, rather than a generic toast.
     */
    private fun showError(error: ChatError) {
        val message = when (error) {
            is ChatError.NoApiKey -> getString(R.string.chat_error_no_key)
            is ChatError.Offline -> getString(R.string.chat_error_offline)
            is ChatError.RateLimited -> getString(R.string.chat_error_rate_limited)
            is ChatError.Upstream -> getString(R.string.chat_error_upstream, error.message)
        }
        com.google.android.material.snackbar.Snackbar
            .make(findViewById(R.id.chatList), message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .show()
    }
}
