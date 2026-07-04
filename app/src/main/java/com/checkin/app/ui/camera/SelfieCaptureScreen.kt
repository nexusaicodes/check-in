package com.checkin.app.ui.camera

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.checkin.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SelfieCaptureScreen"

/**
 * Presence gate: a front-camera capture verified by on-device face detection. The captured image is
 * transient — it is deleted as soon as the outcome is known. After [AuthGate.BIOMETRIC_FALLBACK_AFTER]
 * consecutive failures, device unlock is offered as a fallback. [onAuthSuccess] fires once either
 * path passes.
 */
@Composable
fun SelfieCaptureScreen(
    onAuthSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var failCount by remember { mutableIntStateOf(0) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // Tracks disposal so the async provider callback can release the camera if the gate is gone
    // before the provider resolves (onDispose would otherwise see a null provider and skip unbind).
    val gateDisposed = remember { AtomicBoolean(false) }

    val canUseBiometric = remember {
        activity != null && BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    fun launchBiometric() {
        if (activity == null) {
            errorMessage = context.getString(R.string.biometric_unavailable)
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onAuthSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        errorMessage = errString.toString()
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.biometric_title))
            .setSubtitle(context.getString(R.string.biometric_subtitle))
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    DisposableEffect(Unit) {
        onDispose {
            gateDisposed.set(true)
            cameraProvider?.unbindAll()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        if (gateDisposed.get()) {
                            // Gate left composition before the provider resolved — release the camera
                            // now instead of binding one that nothing remains to unbind.
                            provider.unbindAll()
                            return@addListener
                        }
                        cameraProvider = provider
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.selfie_dismiss),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            successMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    // Announce the capture outcome to TalkBack as it changes.
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (AuthGate.shouldShowHint(failCount) && canUseBiometric) {
                val remaining = AuthGate.attemptsLeft(failCount)
                Text(
                    text = pluralStringResource(R.plurals.selfie_attempts_remaining, remaining, remaining),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                // Icon is decorative — the enclosing button's action is conveyed by capture affordance.
                FilledTonalButton(
                    onClick = {
                        isProcessing = true
                        errorMessage = null
                        captureAndValidate(
                            context = context,
                            imageCapture = imageCapture,
                            onSuccess = {
                                isProcessing = false
                                errorMessage = null
                                successMessage = context.getString(R.string.selfie_face_detected)
                                scope.launch {
                                    delay(800)
                                    onAuthSuccess()
                                }
                            },
                            onNoFace = {
                                isProcessing = false
                                successMessage = null
                                failCount++
                                errorMessage = context.getString(R.string.selfie_no_face)
                            },
                            onError = {
                                isProcessing = false
                                successMessage = null
                                // Count capture/decode errors toward the fallback too, so a camera
                                // that keeps erroring still escalates to device unlock.
                                failCount++
                                errorMessage = context.getString(R.string.selfie_error)
                            },
                            scope = scope
                        )
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.selfie_capture),
                        modifier = Modifier.size(32.dp)
                    )
                }

                if (AuthGate.shouldOfferBiometric(failCount) && canUseBiometric) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { launchBiometric() }) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null, // label text conveys the action
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.biometric_use_device_unlock))
                    }
                }
            }
        }
    }
}

private fun captureAndValidate(
    context: Context,
    imageCapture: ImageCapture,
    onSuccess: () -> Unit,
    onNoFace: () -> Unit,
    onError: () -> Unit,
    scope: CoroutineScope
) {
    val selfiesDir = File(context.filesDir, "selfies").also { it.mkdirs() }
    val outputFile = File(selfiesDir, "${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // Detection and deletion run on an independent IO scope: a mid-capture dismiss or
                // config change must not cancel them and strand the JPEG. The UI result is then
                // re-dispatched to the composition [scope], which silently drops it if the gate is gone.
                CoroutineScope(Dispatchers.IO).launch {
                    var success = false
                    var errored = false
                    try {
                        val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                        if (bitmap == null) {
                            errored = true
                        } else {
                            success = FaceDetectionHelper.detectFace(bitmap)
                            bitmap.recycle()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Face detection failed", e)
                        errored = true
                    } finally {
                        // Selfies are a transient auth gate — never persisted.
                        outputFile.delete()
                    }
                    scope.launch {
                        when {
                            errored -> onError()
                            success -> onSuccess()
                            else -> onNoFace()
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture failed", exception)
                // Clean up any partially written frame from the failed capture.
                outputFile.delete()
                onError()
            }
        }
    )
}
