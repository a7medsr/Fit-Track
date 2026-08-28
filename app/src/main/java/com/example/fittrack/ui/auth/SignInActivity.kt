package com.example.fittrack.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fittrack.MainActivity
import com.example.fittrack.R
import com.example.fittrack.data.auth.GoogleCredentialClient
import com.example.fittrack.data.auth.GoogleCredentialResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The launcher screen. When a session already exists it forwards to the
 * dashboard without drawing anything, so a signed-in user never sees it.
 */
@AndroidEntryPoint
class SignInActivity : AppCompatActivity() {

    private val viewModel: SignInViewModel by viewModels()

    @Inject
    lateinit var googleCredentialClient: GoogleCredentialClient

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var authTitle: TextView
    private lateinit var authSubtitle: TextView
    private lateinit var authMessage: TextView
    private lateinit var primaryBtn: TextView
    private lateinit var toggleModeBtn: TextView
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (viewModel.alreadySignedIn) {
            openApp()
            return
        }

        setContentView(R.layout.activity_sign_in)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        authTitle = findViewById(R.id.authTitle)
        authSubtitle = findViewById(R.id.authSubtitle)
        authMessage = findViewById(R.id.authMessage)
        primaryBtn = findViewById(R.id.primaryBtn)
        toggleModeBtn = findViewById(R.id.toggleModeBtn)
        progress = findViewById(R.id.authProgress)

        primaryBtn.setOnClickListener {
            viewModel.submitEmail(
                emailInput.text.toString(),
                passwordInput.text.toString()
            )
        }
        toggleModeBtn.setOnClickListener { viewModel.toggleMode() }
        findViewById<View>(R.id.forgotBtn).setOnClickListener {
            viewModel.sendPasswordReset(emailInput.text.toString())
        }
        findViewById<View>(R.id.googleBtn).setOnClickListener { startGoogleSignIn() }
        // The app is offline-first, so an account is optional. Skipping keeps
        // everything working locally; signing in later adopts that data.
        findViewById<View>(R.id.skipBtn).setOnClickListener { openApp() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is SignInEvent.Authenticated -> openApp()
                            is SignInEvent.SyncWarning -> Toast.makeText(
                                this@SignInActivity,
                                getString(R.string.auth_sync_warning, event.message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: SignInFormState) {
        val signingUp = state.mode == AuthMode.SIGN_UP

        authTitle.setText(
            if (signingUp) R.string.auth_title_sign_up else R.string.auth_title_sign_in
        )
        authSubtitle.setText(
            if (signingUp) R.string.auth_subtitle_sign_up else R.string.auth_subtitle_sign_in
        )
        primaryBtn.setText(
            if (signingUp) R.string.auth_action_sign_up else R.string.auth_action_sign_in
        )
        toggleModeBtn.setText(
            if (signingUp) R.string.auth_toggle_to_sign_in else R.string.auth_toggle_to_sign_up
        )

        val message = state.error ?: state.notice
        authMessage.text = message.orEmpty()
        authMessage.visibility = if (message == null) View.GONE else View.VISIBLE
        authMessage.setTextColor(
            getColor(if (state.error != null) R.color.danger else R.color.brand_bright)
        )

        progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        primaryBtn.isEnabled = !state.busy
        primaryBtn.alpha = if (state.busy) 0.5f else 1f
    }

    private fun startGoogleSignIn() {
        viewModel.setBusy(true)
        lifecycleScope.launch {
            when (val result = googleCredentialClient.requestIdToken(this@SignInActivity)) {
                is GoogleCredentialResult.Success -> viewModel.submitGoogleToken(result.idToken)
                is GoogleCredentialResult.Cancelled -> viewModel.setBusy(false)
                is GoogleCredentialResult.Failure -> viewModel.reportGoogleFailure(result.message)
            }
        }
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
