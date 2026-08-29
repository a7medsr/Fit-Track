package com.example.fittrack.ui.splash

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.fittrack.R
import com.example.fittrack.ui.auth.SignInActivity

/**
 * The launch screen: an ECG trace drawing itself under the wordmark, with the
 * slogan arriving on the beat.
 *
 * It is deliberately short and always skippable. A launch animation is a first
 * impression exactly once and an obstacle every time after that, so it can be
 * tapped away, it is cut entirely when the system says animations are off, and
 * nothing waits on it -- routing is decided the moment it ends.
 */
class SplashActivity : AppCompatActivity() {

    private var animator: ValueAnimator? = null
    private var spacingAnimator: ValueAnimator? = null
    private var handedOver = false
    private var started = false

    private lateinit var trace: PulseTraceView
    private lateinit var wordmark: TextView
    private lateinit var slogan: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        // Kept purely for the themed splash background, which matches the app
        // background so the system splash hands over without a flash.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        trace = findViewById(R.id.pulseTrace)
        wordmark = findViewById(R.id.splashWordmark)
        slogan = findViewById(R.id.splashSlogan)

        val root = findViewById<View>(R.id.splashRoot)
        root.setOnClickListener { openNext() }

        // Unconditional, and set before anything else can go wrong. The screen
        // is decoration; it must never be the reason the app fails to open.
        // An earlier version started the timer inside the animation callback,
        // and when that callback did not fire the app sat on a black screen
        // for ever.
        root.postDelayed(::openNext, TOTAL_MS)

        if (animationsDisabled()) {
            // Someone who has turned animations off at the system level has
            // asked not to be shown this. Honour it rather than overriding it.
            openNext()
            return
        }

        // Started on the first draw of our own content, which is also the
        // moment the system splash is dismissed. The library's exit listener
        // looks like the tidier hook but does not reliably fire, and relying on
        // it is what left the screen blank.
        root.viewTreeObserver.addOnPreDrawListener(
            object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    root.viewTreeObserver.removeOnPreDrawListener(this)
                    startAnimation()
                    return true
                }
            }
        )
    }

    private fun startAnimation() {
        if (started || handedOver) return
        started = true

        wordmark.translationY = resources.displayMetrics.density * WORDMARK_RISE_DP
        wordmark.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(WORDMARK_DELAY_MS)
            .setDuration(WORDMARK_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // The slogan lands just after the trace hits the spike, so the words
        // arrive on the beat rather than alongside it. Letter spacing opens as
        // it fades, which reads as the line settling into place instead of
        // simply appearing.
        slogan.letterSpacing = SLOGAN_SPACING_FROM
        slogan.translationY = resources.displayMetrics.density * SLOGAN_RISE_DP
        slogan.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(SLOGAN_DELAY_MS)
            .setDuration(SLOGAN_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        spacingAnimator = ValueAnimator.ofFloat(SLOGAN_SPACING_FROM, SLOGAN_SPACING_TO).apply {
            startDelay = SLOGAN_DELAY_MS
            duration = SLOGAN_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { slogan.letterSpacing = it.animatedValue as Float }
            start()
        }

        // One heartbeat through the words once they are readable, so the hold
        // at the end is still moving rather than a frozen frame.
        slogan.postDelayed({
            if (!handedOver) {
                slogan.animate()
                    .scaleX(SLOGAN_BEAT_SCALE).scaleY(SLOGAN_BEAT_SCALE)
                    .setDuration(SLOGAN_BEAT_MS)
                    .withEndAction {
                        slogan.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(SLOGAN_BEAT_MS)
                            .start()
                    }
                    .start()
            }
        }, SLOGAN_BEAT_AT_MS)

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TRACE_DURATION_MS
            startDelay = TRACE_DELAY_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { trace.progress = it.animatedValue as Float }
            start()
        }
    }

    /**
     * Guarded, because three things race to call it: the timer, a tap, and the
     * back press. Without the flag a tap during the last frames starts the next
     * screen twice.
     */
    private fun openNext() {
        if (handedOver) return
        handedOver = true

        animator?.cancel()
        spacingAnimator?.cancel()
        // SignInActivity forwards straight to the dashboard when a session
        // already exists, so the routing decision stays in one place.
        startActivity(Intent(this, SignInActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        animator?.cancel()
        spacingAnimator?.cancel()
        animator = null
        spacingAnimator = null
        super.onDestroy()
    }

    /** True when the user has turned system animations off, or set them to zero. */
    private fun animationsDisabled(): Boolean = runCatching {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    private companion object {
        const val TRACE_DELAY_MS = 120L
        const val TRACE_DURATION_MS = 1150L
        const val WORDMARK_DELAY_MS = 80L
        const val WORDMARK_DURATION_MS = 520L
        const val WORDMARK_RISE_DP = 16f
        const val SLOGAN_DELAY_MS = 700L
        const val SLOGAN_DURATION_MS = 700L
        const val SLOGAN_RISE_DP = 10f
        const val SLOGAN_SPACING_FROM = 0.42f
        const val SLOGAN_SPACING_TO = 0.16f
        const val SLOGAN_BEAT_AT_MS = 1700L
        const val SLOGAN_BEAT_MS = 260L
        const val SLOGAN_BEAT_SCALE = 1.07f
        /**
         * Long enough to read the slogan twice over, which is the point of the
         * screen. Still skippable on tap, so it costs a returning user nothing.
         */
        const val TOTAL_MS = 3000L
    }
}
