package com.checkin.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.checkin.app.service.CheckInService
import com.checkin.app.service.PresenceCheckSignal
import com.checkin.app.service.PresenceCheckSignal.Reason
import com.checkin.app.ui.camera.PresenceGate
import com.checkin.app.ui.navigation.AppNavScaffold
import com.checkin.app.ui.theme.CheckInAppTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        handlePresenceIntent(intent)

        setContent {
            CheckInAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val gateReason by PresenceCheckSignal.request.collectAsStateWithLifecycle()

                    // Hoisted above the gate/host switch so entering and leaving the presence gate
                    // never destroys the nav controller — the active tab and back stack survive
                    // re-auth.
                    val navController = rememberNavController()
                    if (gateReason != Reason.NONE) {
                        // Full-screen modal gate: the nav host is not composed underneath, so
                        // nothing behind it is reachable by touch, accessibility focus, or the
                        // camera. Back dismisses the gate rather than the (absent) host.
                        BackHandler { PresenceCheckSignal.clear() }
                        PresenceGate(
                            onAuthSuccess = { onRootGatePassed() },
                            onDismiss = { PresenceCheckSignal.clear() },
                        )
                    } else {
                        AppNavScaffold(navController)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePresenceIntent(intent)
    }

    /**
     * Drops a gate the user opened and walked away from.
     *
     * Checked on every start rather than on stop, because leaving is exactly what the gate does on
     * its legitimate path: the camera-recovery screen sends the user to system settings and expects
     * them back. That round trip is seconds; an abandoned request is hours, and only the clock can
     * tell the two apart.
     */
    override fun onStart() {
        super.onStart()
        val container = (application as CheckInApplication).container
        PresenceCheckSignal.expireIfStale(container.timeSource.nowMillis())

        // The most reliable revive point there is: a visible Activity is always allowed to start a
        // foreground service, where the background callers may be refused. Without it, opening the
        // app on a session whose service had been killed showed a running timer — rendered from the
        // row — with nothing behind it, which is exactly how a lost session used to stay lost.
        container.applicationScope.launch {
            container.sessionWatchdog.reviveIfNeeded(source = "app open")
        }
    }

    private fun handlePresenceIntent(intent: Intent?) {
        // One-shot: consume the extra so an Activity recreation (rotation, theme change) doesn't
        // replay the notification tap and re-open a gate the user already handled.
        when {
            intent?.getBooleanExtra(CheckInService.EXTRA_CHECK_OUT, false) == true -> {
                intent.removeExtra(CheckInService.EXTRA_CHECK_OUT)
                requestPresenceCheck(Reason.CHECK_OUT)
            }
            intent?.getBooleanExtra(CheckInService.EXTRA_PRESENCE_CHECK, false) == true -> {
                intent.removeExtra(CheckInService.EXTRA_PRESENCE_CHECK)
                requestPresenceCheck(Reason.REAUTH)
            }
            intent?.getBooleanExtra(CheckInService.EXTRA_CHECK_IN, false) == true -> {
                intent.removeExtra(CheckInService.EXTRA_CHECK_IN)
                // The tap itself is worth recording even when the gate can't run — it is what the
                // user did with the notification, not what the app managed to do about it.
                (application as CheckInApplication).container.let { container ->
                    container.applicationScope.launch {
                        container.engagementReporter.onNudgeOpened(container.timeSource.nowMillis())
                    }
                }
                requestPresenceCheck(Reason.CHECK_IN)
            }
        }
    }

    /**
     * Raises the gate unconditionally. [PresenceGate] owns the disclosure and both permissions, so
     * there is no screen a request can arrive behind and queue up on — it always opens on the spot.
     * What it can still do is sit unanswered if the user walks away from it, which is what the
     * timestamp is for; [onStart] retires anything stale before it can reopen.
     */
    private fun requestPresenceCheck(reason: Reason) {
        val container = (application as CheckInApplication).container
        PresenceCheckSignal.raise(reason, container.timeSource.nowMillis())
    }

    /**
     * Resolves the root gate: re-auth re-arms the reminder, a check-out request closes the session,
     * and a nudge tap opens one.
     */
    private fun onRootGatePassed() {
        val container = (application as CheckInApplication).container
        when (PresenceCheckSignal.request.value) {
            Reason.REAUTH -> container.serviceController.rearm(fromNotification = true)
            Reason.CHECK_OUT -> container.applicationScope.launch {
                container.repository.checkOutActiveSession()
                container.serviceController.stop()
            }
            Reason.CHECK_IN -> container.applicationScope.launch {
                // Guard against a stale nudge: the user may have already checked in between the
                // notification being posted and being tapped.
                if (container.repository.getActiveSession() == null) {
                    container.settings.seedTrackingStartIfNeeded()
                    val session = container.repository.checkIn()
                    container.serviceController.startTimer(session.id, session.startedAt)
                    container.engagementReporter.onCheckedIn(session.startedAt)
                }
            }
            Reason.NONE -> {}
        }
        PresenceCheckSignal.clear()
    }
}
