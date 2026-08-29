package com.example.fittrack.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
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
 *
 * Google is the only sign-in method. That is what makes the name and email on
 * an account something Google has verified rather than something the user
 * typed, which matters now that both are shown to strangers in a community.
 */
@AndroidEntryPoint
class SignInActivity : AppCompatActivity() {

    private val viewModel: SignInViewModel by viewModels()

    @Inject
    lateinit var googleCredentialClient: GoogleCredentialClient

    private lateinit var authMessage: TextView
    private lateinit var googleBtn: TextView
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (viewModel.alreadySignedIn) {
            openApp()
            return
        }

        setContentView(R.layout.activity_sign_in)

        authMessage = findViewById(R.id.authMessage)
        googleBtn = findViewById(R.id.googleBtn)
        progress = findViewById(R.id.authProgress)

        googleBtn.setOnClickListener { startGoogleSignIn() }
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
        authMessage.text = state.error.orEmpty()
        authMessage.visibility = if (state.error == null) View.GONE else View.VISIBLE
        authMessage.setTextColor(getColor(R.color.danger))

        progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        googleBtn.isEnabled = !state.busy
        googleBtn.alpha = if (state.busy) 0.5f else 1f
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
