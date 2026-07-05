package com.checkin.app.di

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.checkin.app.service.CheckInService

/** Seam over the [CheckInService] foreground-service intents so ViewModels don't hold a Context. */
interface ServiceController {
    fun startTimer(sessionId: Long, startedAt: Long)
    fun stop()
    fun rearm()
}

class DefaultServiceController(private val context: Context) : ServiceController {

    override fun startTimer(sessionId: Long, startedAt: Long) {
        val intent = Intent(context, CheckInService::class.java).apply {
            action = CheckInService.ACTION_START
            putExtra(CheckInService.EXTRA_SESSION_ID, sessionId)
            putExtra(CheckInService.EXTRA_START_TIME, startedAt)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stop() {
        context.startService(
            Intent(context, CheckInService::class.java).apply { action = CheckInService.ACTION_STOP }
        )
    }

    override fun rearm() {
        context.startService(
            Intent(context, CheckInService::class.java).apply { action = CheckInService.ACTION_REARM_REMINDER }
        )
    }
}
