package com.checkin.app.ui.camera

import android.content.Context
import java.io.File

/**
 * Owns the transient-selfie scratch directory. A captured frame is deleted as soon as face detection
 * resolves; [sweep] clears any orphan left behind by process death between the frame write and that
 * delete, so nothing ever accumulates in `filesDir/selfies/`.
 */
object SelfieStorage {
    private const val DIR_NAME = "selfies"

    fun dir(context: Context): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun sweep(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
