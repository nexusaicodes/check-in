package com.checkin.app

import android.app.Application
import com.checkin.app.di.AppContainer
import com.checkin.app.di.DefaultAppContainer
import com.checkin.app.notify.NotificationChannels
import com.checkin.app.notify.engagement.NudgeWorker

/** Owns the app-wide [AppContainer] (manual DI — no framework). */
class CheckInApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        // Registered here rather than in the service, so a channel exists before anything tries to
        // post to it — the engagement pass can run without the service ever having started.
        NotificationChannels.ensureAll(this)
        NudgeWorker.schedule(this)
    }
}
