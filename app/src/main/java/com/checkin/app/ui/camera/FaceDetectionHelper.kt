package com.checkin.app.ui.camera

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "FaceDetectionHelper"

object FaceDetectionHelper {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setMinFaceSize(0.15f)
        .build()

    private val detector = FaceDetection.getClient(options)

    /** Returns true if at least one face is detected in the bitmap. */
    suspend fun detectFace(bitmap: Bitmap): Boolean = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        Log.d(TAG, "Running face detection on ${bitmap.width}x${bitmap.height} bitmap")
        detector.process(image)
            .addOnSuccessListener { faces ->
                Log.d(TAG, "Detection complete: ${faces.size} face(s) found")
                if (cont.isActive) cont.resume(faces.isNotEmpty())
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Detection failed", e)
                if (cont.isActive) cont.resume(false)
            }
    }
}
