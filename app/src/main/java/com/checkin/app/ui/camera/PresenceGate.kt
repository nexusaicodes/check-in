package com.checkin.app.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.checkin.app.CheckInApplication
import com.checkin.app.R

/** Camera drives the presence check; notifications carry the running timer and its reminder. */
private val PRESENCE_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.POST_NOTIFICATIONS,
)

/**
 * The one entry point to a presence check, and the only place either permission is asked for.
 *
 * Everything the check needs is raised here, in the order Play policy requires and at the moment the
 * feature actually demands it: the prominent disclosure, then the runtime permissions, then the
 * capture itself. Nothing is asked at launch, so the app is fully browsable before a first check-in.
 * [onDismiss] backs out at any stage, leaving the caller's action unperformed.
 */
@Composable
fun PresenceGate(onAuthSuccess: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) {
        (context.applicationContext as CheckInApplication).container.settings
    }

    var disclosureSeen by remember { mutableStateOf(settings.hasSeenCameraDisclosure()) }
    var cameraGranted by remember { mutableStateOf(context.hasCameraPermission()) }
    // Separates "not asked yet" from "asked and refused" — only the latter earns the recovery screen.
    var cameraRefused by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Only the camera decides whether the check can run. A refused POST_NOTIFICATIONS costs the
        // timer notification and the presence reminder, both of which Notifier already guards.
        cameraGranted = context.hasCameraPermission()
        cameraRefused = !cameraGranted
    }

    // Asking is a side effect of reaching the "disclosed but ungranted" state, so accepting the
    // disclosure and arriving with a revoked permission both land on the same system dialog.
    LaunchedEffect(disclosureSeen, cameraGranted) {
        if (disclosureSeen && !cameraGranted && !requested) {
            requested = true
            permissionLauncher.launch(PRESENCE_PERMISSIONS)
        }
    }

    // The recovery screen sends the user to system settings; this is what notices they came back.
    LifecycleResumeEffect(Unit) {
        if (!cameraGranted && context.hasCameraPermission()) {
            cameraGranted = true
            cameraRefused = false
        }
        onPauseOrDispose {}
    }

    when {
        !disclosureSeen -> CameraDisclosureScreen(
            onAccept = {
                settings.markCameraDisclosureSeen()
                disclosureSeen = true
            },
            onDismiss = onDismiss,
        )

        cameraGranted -> SelfieCaptureScreen(onAuthSuccess = onAuthSuccess, onDismiss = onDismiss)

        cameraRefused -> CameraPermissionScreen(
            onGrant = {
                val activity = context.findActivity()
                // Android stops showing the dialog after a second refusal, at which point the only
                // route left is system settings.
                if (activity != null && activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    permissionLauncher.launch(PRESENCE_PERMISSIONS)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                }
            },
            onDismiss = onDismiss,
        )

        // The system dialog is up: hold the full-screen surface rather than flashing a screen behind it.
        else -> Box(modifier = Modifier.fillMaxSize())
    }
}

/** Recovery from a refused camera permission: retry, or open system settings once retrying is spent. */
@Composable
private fun CameraPermissionScreen(onGrant: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.camera_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.camera_permission_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGrant) {
            Text(stringResource(R.string.camera_permission_button))
        }
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.presence_gate_cancel))
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Unwraps the theming/base-context wrappers Compose may hand out to reach the hosting Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
