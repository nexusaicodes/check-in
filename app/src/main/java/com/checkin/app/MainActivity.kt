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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.checkin.app.service.PresenceCheckSignal
import com.checkin.app.service.StopwatchService
import com.checkin.app.ui.camera.SelfieCaptureScreen
import com.checkin.app.ui.navigation.BottomNavigationBar
import com.checkin.app.ui.navigation.NavigationGraph
import com.checkin.app.ui.theme.CheckInAppTheme

class MainActivity : FragmentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val allPermissionsGranted = mutableStateOf(false)

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
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        handlePresenceIntent(intent)

        allPermissionsGranted.value = hasAllPermissions()
        if (!allPermissionsGranted.value) {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            CheckInAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val presenceCheck by PresenceCheckSignal.requested.collectAsState()

                    if (!allPermissionsGranted.value) {
                        PermissionGate()
                    } else {
                        // Hoisted above the gate/host switch so entering and leaving the presence
                        // gate never destroys the nav controller — the active tab and back stack
                        // survive re-auth.
                        val navController = rememberNavController()
                        if (presenceCheck) {
                            // Full-screen modal gate: the nav host is not composed underneath, so
                            // nothing behind it is reachable by touch, accessibility focus, or the
                            // camera. Back dismisses the gate rather than the (absent) host.
                            BackHandler { PresenceCheckSignal.requested.value = false }
                            SelfieCaptureScreen(
                                onAuthSuccess = {
                                    startService(
                                        Intent(this@MainActivity, StopwatchService::class.java).apply {
                                            action = StopwatchService.ACTION_REARM_REMINDER
                                        }
                                    )
                                    PresenceCheckSignal.requested.value = false
                                },
                                onDismiss = { PresenceCheckSignal.requested.value = false }
                            )
                        } else {
                            Scaffold(
                                bottomBar = { BottomNavigationBar(navController) }
                            ) { paddingValues ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(paddingValues)
                                ) {
                                    NavigationGraph(navController)
                                }
                            }
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
        if (intent?.getBooleanExtra(StopwatchService.EXTRA_PRESENCE_CHECK, false) == true) {
            // One-shot: consume the extra so an Activity recreation (rotation, theme change)
            // doesn't replay the reminder tap and re-open the gate the user already handled.
            intent.removeExtra(StopwatchService.EXTRA_PRESENCE_CHECK)
            PresenceCheckSignal.requested.value = true
        }
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
