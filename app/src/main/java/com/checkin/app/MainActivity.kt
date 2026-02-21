package com.checkin.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.checkin.app.ui.navigation.BottomNavigationBar
import com.checkin.app.ui.navigation.NavigationGraph
import com.checkin.app.ui.theme.CheckInAppTheme

class MainActivity : ComponentActivity() {

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
                    if (allPermissionsGranted.value) {
                        val navController = rememberNavController()
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
                    } else {
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
                                    // If rationale can still be shown, re-request; otherwise send to Settings
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
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check when returning from Settings
        if (!allPermissionsGranted.value && hasAllPermissions()) {
            allPermissionsGranted.value = true
        }
    }
}
