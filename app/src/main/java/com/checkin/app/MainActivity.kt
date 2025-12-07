package com.checkin.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.checkin.app.ui.navigation.BottomNavigationBar
import com.checkin.app.ui.navigation.NavigationGraph
import com.checkin.app.ui.theme.CheckInAppTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        when {
            isGranted -> {
                setupNotifications()
            }

            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                // User denied with "Don't ask again" - guide to settings
                showSettingsDialog()
            }

            else -> {
                // User denied but can ask again
                showPermissionDeniedMessage()
            }
        }
    }

    private fun requestNotificationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                setupNotifications()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                // Show educational UI explaining why you need permission
                showRationaleDialog()
            }

            else -> {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_notification_title))
            .setMessage(getString(R.string.permission_notification_message))
            .setPositiveButton(getString(R.string.permission_button_ok)) { _, _ ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(getString(R.string.permission_button_cancel), null)
            .show()
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_required_title))
            .setMessage(getString(R.string.permission_required_message))
            .setPositiveButton(getString(R.string.permission_button_settings)) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(getString(R.string.permission_button_cancel), null)
            .show()
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            startActivity(this)
        }
    }

    private fun setupNotifications() {
        // Setup notification channels and functionality
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(this, getString(R.string.permission_notifications_disabled), Toast.LENGTH_SHORT).show()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestNotificationPermission()

        setContent {
            CheckInAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
                }
            }
        }
    }
}
