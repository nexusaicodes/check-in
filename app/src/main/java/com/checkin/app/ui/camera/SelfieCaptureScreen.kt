package com.checkin.app.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.checkin.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private const val TAG = "SelfieCaptureScreen"

@Composable
fun SelfieCaptureScreen(
    onSelfieCaptured: (filePath: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Camera preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
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

        // Close button top-left
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

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Success message
            successMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Error message
            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
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
                // Capture button
                FilledTonalButton(
                    onClick = {
                        isProcessing = true
                        errorMessage = null
                        captureAndValidate(
                            context = context,
                            imageCapture = imageCapture,
                            onSuccess = { filePath ->
                                isProcessing = false
                                errorMessage = null
                                successMessage = context.getString(R.string.selfie_face_detected)
                                scope.launch {
                                    delay(800)
                                    onSelfieCaptured(filePath)
                                }
                            },
                            onNoFace = {
                                isProcessing = false
                                successMessage = null
                                errorMessage = context.getString(R.string.selfie_no_face)
                            },
                            onError = {
                                isProcessing = false
                                successMessage = null
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
            }
        }
    }
}

private fun captureAndValidate(
    context: Context,
    imageCapture: ImageCapture,
    onSuccess: (String) -> Unit,
    onNoFace: () -> Unit,
    onError: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val selfiesDir = File(context.filesDir, "selfies").also { it.mkdirs() }
    val outputFile = File(selfiesDir, "${System.currentTimeMillis()}.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                scope.launch {
                    try {
                        val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                        if (bitmap == null) {
                            outputFile.delete()
                            onError()
                            return@launch
                        }

                        // Mirror the bitmap (front camera is mirrored)
                        val mirrored = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height,
                            Matrix().apply { preScale(-1f, 1f) }, false
                        )

                        // Compress and overwrite
                        FileOutputStream(outputFile).use { out ->
                            mirrored.compress(Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        bitmap.recycle()
                        mirrored.recycle()

                        val hasFace = FaceDetectionHelper.detectFace(
                            BitmapFactory.decodeFile(outputFile.absolutePath)
                        )

                        if (hasFace) {
                            onSuccess(outputFile.absolutePath)
                        } else {
                            outputFile.delete()
                            onNoFace()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Face detection failed", e)
                        outputFile.delete()
                        onError()
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture failed", exception)
                onError()
            }
        }
    )
}
