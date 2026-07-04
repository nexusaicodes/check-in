package com.checkin.app

import android.app.Application
import com.checkin.app.di.AppContainer
import com.checkin.app.di.DefaultAppContainer

/** Owns the app-wide [AppContainer] (manual DI — no framework). */
class CheckInApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
