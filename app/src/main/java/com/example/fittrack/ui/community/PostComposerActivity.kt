package com.example.fittrack.ui.community

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.example.fittrack.R
import com.example.fittrack.domain.model.CommunityLimits
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Writes one post: text, and optionally a photo. */
@AndroidEntryPoint
class PostComposerActivity : AppCompatActivity() {

    private val viewModel: PostComposerViewModel by viewModels()

    private lateinit var input: EditText
    private lateinit var counter: TextView
    private lateinit var imageFrame: View
    private lateinit var imagePreview: ImageView
    private lateinit var addImageBtn: TextView
    private lateinit var postBtn: TextView
    private lateinit var errorText: TextView

    /**
     * The system photo picker needs no storage permission on any supported
     * version -- it hands back a single grant for the one image chosen.
     */
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.pickImage(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_post_composer)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBar)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<TextView>(R.id.screenTitle).setText(R.string.community_post_title)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        input = findViewById(R.id.postInput)
        counter = findViewById(R.id.counter)
        imageFrame = findViewById(R.id.imageFrame)
        imagePreview = findViewById(R.id.imagePreview)
        addImageBtn = findViewById(R.id.addImageBtn)
        postBtn = findViewById(R.id.postBtn)
        errorText = findViewById(R.id.errorText)

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateCounter(s?.length ?: 0)
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })
        updateCounter(0)

        addImageBtn.setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        findViewById<View>(R.id.removeImageBtn).setOnClickListener { viewModel.pickImage(null) }
        postBtn.setOnClickListener { viewModel.post(input.text.toString()) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch {
                    viewModel.posted.collect {
                        Toast.makeText(
                            this@PostComposerActivity,
                            R.string.community_posted,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
            }
        }
    }

    private fun render(state: ComposerState) {
        if (state.image == null) {
            imageFrame.visibility = View.GONE
            addImageBtn.setText(R.string.community_add_photo)
        } else {
            imageFrame.visibility = View.VISIBLE
            imagePreview.load(state.image)
            addImageBtn.setText(R.string.community_change_photo)
        }

        postBtn.isEnabled = !state.busy
        postBtn.alpha = if (state.busy) 0.5f else 1f
        // Uploading a photo takes noticeably longer than posting text, so the
        // button says which of the two is happening.
        postBtn.setText(
            when {
                !state.busy -> R.string.community_action_post
                state.image != null -> R.string.community_uploading
                else -> R.string.community_posting
            }
        )

        errorText.text = state.error.orEmpty()
        errorText.visibility = if (state.error == null) View.GONE else View.VISIBLE
    }

    private fun updateCounter(length: Int) {
        counter.text = getString(
            R.string.community_counter,
            length,
            CommunityLimits.POST_TEXT_MAX
        )
        counter.setTextColor(
            getColor(
                if (length > CommunityLimits.POST_TEXT_MAX) R.color.danger else R.color.text_tertiary
            )
        )
    }

    companion object {
        const val EXTRA_ID = "communityId"

        fun intent(context: Context, communityId: String): Intent =
            Intent(context, PostComposerActivity::class.java).putExtra(EXTRA_ID, communityId)
    }
}
