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
                        BackHandler { PresenceCheckSignal.request.value = Reason.NONE }
                        PresenceGate(
                            onAuthSuccess = { onRootGatePassed() },
                            onDismiss = { PresenceCheckSignal.request.value = Reason.NONE },
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
     * there is no longer a screen a request could arrive behind and sit latched in front of —
     * [PresenceCheckSignal] has no expiry, and a reason held now would fire against an hours-stale
     * tap, checking the user in on whatever day it had become by then.
     */
    private fun requestPresenceCheck(reason: Reason) {
        PresenceCheckSignal.request.value = reason
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
        PresenceCheckSignal.request.value = Reason.NONE
    }
}
