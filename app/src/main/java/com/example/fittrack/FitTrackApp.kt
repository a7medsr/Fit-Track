package com.example.fittrack

import android.app.Application
import com.example.fittrack.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FitTrackApp : Application() {

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override fun onCreate() {
        super.onCreate()
        // Watches the Room flows and mirrors changes up while signed in.
        syncScheduler.start()
    }
}