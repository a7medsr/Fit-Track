package com.example.fittrack.data.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client-side request throttle.
 *
 * Free tiers are rate limited per minute, and hitting the ceiling returns a hard
 * error that costs the user their answer. Waiting a couple of seconds is a much
 * better outcome than a red toast, so requests queue instead of failing.
 */
@Singleton
class RateLimiter @Inject constructor() {

    private val mutex = Mutex()
    private val recent = ArrayDeque<Long>()

    /** Suspends until sending now would stay under the limit. */
    suspend fun acquire(now: () -> Long = System::currentTimeMillis) {
        while (true) {
            val waitFor = mutex.withLock {
                val cutoff = now() - WINDOW_MS
                while (recent.isNotEmpty() && recent.first() < cutoff) recent.removeFirst()

                if (recent.size < MAX_PER_WINDOW) {
                    recent.addLast(now())
                    0L
                } else {
                    // Wait until the oldest request falls out of the window.
                    (recent.first() + WINDOW_MS) - now() + SLACK_MS
                }
            }
            if (waitFor <= 0L) return
            delay(waitFor)
        }
    }

    companion object {
        const val WINDOW_MS = 60_000L
        /** Conservative: comfortably under the usual free-tier per-minute ceiling. */
        const val MAX_PER_WINDOW = 10
        private const val SLACK_MS = 50L
    }
}
