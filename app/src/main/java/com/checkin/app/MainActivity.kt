package com.checkin.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.checkin.app.service.PresenceCheckSignal
import com.checkin.app.service.PresenceCheckSignal.Reason
import com.checkin.app.service.CheckInService
import com.checkin.app.ui.camera.CameraDisclosureScreen
import com.checkin.app.ui.camera.SelfieCaptureScreen
import com.checkin.app.ui.navigation.AppNavScaffold
import com.checkin.app.ui.theme.CheckInAppTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val allPermissionsGranted = mutableStateOf(false)

    // Gates the whole permission flow: the camera prominent disclosure must precede the CAMERA prompt.
    private val disclosureSeen = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        allPermissionsGranted.value = results.all { it.value }
    }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        handlePresenceIntent(intent)

        disclosureSeen.value =
            (application as CheckInApplication).container.settings.hasSeenCameraDisclosure()
        allPermissionsGranted.value = hasAllPermissions()
        // Only prompt eagerly once the disclosure has been accepted; before that, the disclosure
        // screen raises the prompt on accept, so the system dialog never precedes the disclosure.
        if (disclosureSeen.value && !allPermissionsGranted.value) {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            CheckInAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val gateReason by PresenceCheckSignal.request.collectAsStateWithLifecycle()

                    if (!disclosureSeen.value) {
                        CameraDisclosureScreen(onAccept = { onCameraDisclosureAccepted() })
                    } else if (!allPermissionsGranted.value) {
                        PermissionGate()
                    } else {
                        // Hoisted above the gate/host switch so entering and leaving the presence
                        // gate never destroys the nav controller — the active tab and back stack
                        // survive re-auth.
                        val navController = rememberNavController()
                        if (gateReason != Reason.NONE) {
                            // Full-screen modal gate: the nav host is not composed underneath, so
                            // nothing behind it is reachable by touch, accessibility focus, or the
                            // camera. Back dismisses the gate rather than the (absent) host.
                            BackHandler { PresenceCheckSignal.request.value = Reason.NONE }
                            SelfieCaptureScreen(
                                onAuthSuccess = { onRootGatePassed() },
                                onDismiss = { PresenceCheckSignal.request.value = Reason.NONE }
                            )
                        } else {
                            AppNavScaffold(navController)
                        }
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
                PresenceCheckSignal.request.value = Reason.CHECK_OUT
            }
            intent?.getBooleanExtra(CheckInService.EXTRA_PRESENCE_CHECK, false) == true -> {
                intent.removeExtra(CheckInService.EXTRA_PRESENCE_CHECK)
                PresenceCheckSignal.request.value = Reason.REAUTH
            }
        }
    }

    /** Persists disclosure acceptance, then raises the permission prompt it must precede. */
    private fun onCameraDisclosureAccepted() {
        (application as CheckInApplication).container.settings.markCameraDisclosureSeen()
        disclosureSeen.value = true
        if (hasAllPermissions()) {
            allPermissionsGranted.value = true
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    /** Resolves the root gate: re-auth re-arms the reminder; a check-out request closes the session. */
    private fun onRootGatePassed() {
        val container = (application as CheckInApplication).container
        when (PresenceCheckSignal.request.value) {
            Reason.REAUTH -> container.serviceController.rearm()
            Reason.CHECK_OUT -> container.applicationScope.launch {
                container.repository.checkOutActiveSession()
                container.serviceController.stop()
            }
            Reason.NONE -> {}
        }
        PresenceCheckSignal.request.value = Reason.NONE
    }

    override fun onResume() {
        super.onResume()
        // Re-check when returning from Settings
        if (!allPermissionsGranted.value && hasAllPermissions()) {
            allPermissionsGranted.value = true
        }
    }

    @androidx.compose.runtime.Composable
    private fun PermissionGate() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = getString(R.string.permission_required_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = getString(R.string.permission_gate_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = {
                if (hasAllPermissions()) {
                    allPermissionsGranted.value = true
                } else {
                    val anyPermanentlyDenied = requiredPermissions.any {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED &&
                            !shouldShowRequestPermissionRationale(it)
                    }
                    if (anyPermanentlyDenied) {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                        )
                    } else {
                        permissionLauncher.launch(requiredPermissions)
                    }
                }
            }) {
                Text(getString(R.string.permission_button_grant))
            }
        }
    }
}
